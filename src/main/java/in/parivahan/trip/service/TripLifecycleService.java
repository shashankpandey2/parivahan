package in.parivahan.trip.service;

import in.parivahan.common.domain.RideTier;
import in.parivahan.common.events.MatchConfirmedEvent;
import in.parivahan.common.events.PaymentCompletedEvent;
import in.parivahan.common.events.PaymentFailedEvent;
import in.parivahan.common.events.TripEndedEvent;
import in.parivahan.common.events.NotificationRequestedEvent;
import in.parivahan.common.events.PaymentInitiatedEvent;
import in.parivahan.common.events.TripStartedEvent;
import in.parivahan.trip.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates all trip state transitions.
 *
 * Every method is @Transactional: DB write + Kafka publish happen in the same logical unit.
 * In production the outbox pattern should replace the direct Kafka publish to avoid the
 * dual-write consistency gap.
 *
 * Redis keys managed here: trip:state:<tripId>, trip:surge:<tripId>,
 * driver:current-trip:<driverId>.
 */
@Service
public class TripLifecycleService {

  private static final Logger log = LoggerFactory.getLogger(TripLifecycleService.class);

  private final FareCalculator       fareCalculator;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final StringRedisTemplate  redisTemplate;
  private final TripRepository       tripRepository;
  private final TripPauseRepository  pauseRepository;
  private final TripEventRepository  eventRepository;

  public TripLifecycleService(FareCalculator fareCalculator,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              StringRedisTemplate redisTemplate,
                              TripRepository tripRepository,
                              TripPauseRepository pauseRepository,
                              TripEventRepository eventRepository) {
    this.fareCalculator  = fareCalculator;
    this.kafkaTemplate   = kafkaTemplate;
    this.redisTemplate   = redisTemplate;
    this.tripRepository  = tripRepository;
    this.pauseRepository = pauseRepository;
    this.eventRepository = eventRepository;
  }

  // ── Kafka consumer ────────────────────────────────────────────────────────

  /**
   * Consume dispatch.match.confirmed → create Trip in MATCHED state.
   * Locks the surge multiplier in Redis so fare is stable for the life of the trip.
   *
   * pickup/dropoff are forwarded from the original ride.requested payload inside this event
   * so the receipt API can return full address details without querying ride_requests.
   */
  @KafkaListener(topics = "dispatch.match.confirmed", groupId = "trip-service")
  @Transactional
  public Trip onMatchConfirmed(MatchConfirmedEvent event) {
    log.info("creating trip rideId={} tripId={} region={} tier={}",
        event.rideId(), event.tripId(), event.regionId(), event.tier());

    Trip trip = new Trip(
        event.tripId(),
        event.rideId(),
        event.driverId(),
        event.riderId(),
        event.regionId(),
        event.tier(),
        event.paymentMethod(),
        event.pickup(),
        event.dropoff(),
        BigDecimal.valueOf(event.surgeMultiplier())
    );
    tripRepository.save(trip);

    // 24 h is more than any realistic trip duration
    redisTemplate.opsForValue().set(
        "trip:surge:" + trip.getTripId(),
        event.surgeMultiplier() + "",
        24, TimeUnit.HOURS
    );
    setTripState(trip.getTripId(), TripStatus.MATCHED);
    auditEvent(trip.getTripId(), TripEventType.MATCHED, null, null, trip.getMatchedAt());

    // bridge the match event to a rider push notification
    NotificationRequestedEvent rideMatchedNotif = NotificationRequestedEvent.create(
        trip.getRiderId(),
        List.of("PUSH"),
        "RIDE_MATCHED",
        Map.of("tripId",      trip.getTripId(),
               "driverId",    trip.getDriverId(),
               "etaMinutes",  String.valueOf(event.etaMinutes())));
    kafkaTemplate.send("notification.requested", trip.getRiderId(), rideMatchedNotif);

    return trip;
  }

  // ── Kafka consumers (payment feedback loop) ─────────────────────────────

  /**
   * Consume payment.completed → persist payment identifiers and mark trip paid.
   * Trip Service is the sole writer of the trips table.
   */
  @KafkaListener(topics = "payment.completed", groupId = "trip-service")
  @Transactional
  public void onPaymentCompleted(PaymentCompletedEvent event) {
    log.info("payment completed tripId={} paymentId={}", event.tripId(), event.paymentId());
    tripRepository.completePayment(event.tripId(), event.paymentId(), event.pspReference());
  }

  /**
   * Consume payment.failed → update trips.payment_status = FAILED.
   * Emitted by Payment Service after all PSP retries are exhausted.
   */
  @KafkaListener(topics = "payment.failed", groupId = "trip-service")
  @Transactional
  public void onPaymentFailed(PaymentFailedEvent event) {
    log.warn("payment failed tripId={} paymentId={} reason={} attempts={}",
        event.tripId(), event.paymentId(), event.failureReason(), event.attemptsExhausted());
    tripRepository.updatePaymentStatus(event.tripId(), PaymentStatus.FAILED);
  }

  // ── REST handlers ─────────────────────────────────────────────────────────

  /**
   * Driver POSTs /trips/{id}/start → transition MATCHED → IN_PROGRESS.
   */
  @Transactional
  public void startTrip(String tripId) {
    Trip trip = tripRepository.findByIdOrThrow(tripId);
    trip.start();
    tripRepository.save(trip);

    redisTemplate.opsForValue().set("driver:current-trip:" + trip.getDriverId(), tripId);
    setTripState(tripId, TripStatus.IN_PROGRESS);

    auditEvent(tripId, TripEventType.STARTED, null, null, trip.getStartedAt());

    TripStartedEvent event = TripStartedEvent.create(
        trip.getTripId(), trip.getRideId(),
        trip.getDriverId(), trip.getRiderId(),
        trip.getStartedAt()
    );
    kafkaTemplate.send("trip.started", trip.getTripId(), event);

    NotificationRequestedEvent startedNotif = NotificationRequestedEvent.create(
        trip.getRiderId(),
        List.of("PUSH"),
        "TRIP_STARTED",
        Map.of("tripId", tripId, "startedAt", trip.getStartedAt().toString()));
    kafkaTemplate.send("notification.requested", trip.getRiderId(), startedNotif);
    log.info("trip started tripId={}", tripId);
  }

  /**
   * Driver POSTs /trips/{id}/arrive → driver is physically at pickup location.
   * Does NOT change FSM state — triggers DRIVER_ARRIVED push notification only.
   * Audit event type: ARRIVED.
   */
  @Transactional
  public void arriveTrip(String tripId) {
    Instant arrivedAt = Instant.now();
    Trip trip = tripRepository.findByIdOrThrow(tripId);
    // Guard: driver can only signal arrival while trip is in MATCHED state
    if (trip.getStatus() != TripStatus.MATCHED) {
      throw new InvalidTripStateException(
          "Cannot signal arrival in state: " + trip.getStatus());
    }
    auditEvent(tripId, TripEventType.ARRIVED, null, null, arrivedAt);
    NotificationRequestedEvent arrivedNotif = NotificationRequestedEvent.create(
        trip.getRiderId(),
        List.of("PUSH"),
        "DRIVER_ARRIVED",
        Map.of("tripId", tripId,
               "driverId", trip.getDriverId(),
               "arrivedAt", arrivedAt.toString()));
    kafkaTemplate.send("notification.requested", trip.getRiderId(), arrivedNotif);
    log.info("driver arrived tripId={}", tripId);
  }

  /**
   * Driver POSTs /trips/{id}/pause → transition IN_PROGRESS → PAUSED.
   *
   * @param reason one of: TRAFFIC, BREAKDOWN, REQUESTED_BY_RIDER, OTHER
   */
  @Transactional
  public void pauseTrip(String tripId, String reason) {
    Instant pausedAt = Instant.now();
    Trip trip = tripRepository.findByIdOrThrow(tripId);
    trip.pause();
    tripRepository.save(trip);

    TripPause pause = new TripPause(UUID.randomUUID().toString(), tripId, pausedAt, reason);
    pauseRepository.save(pause);
    setTripState(tripId, TripStatus.PAUSED);

    auditEvent(tripId, TripEventType.PAUSED, null, null, pausedAt);
    log.info("trip paused tripId={} reason={}", tripId, reason);
  }

  /**
   * Driver POSTs /trips/{id}/resume → transition PAUSED → IN_PROGRESS.
   */
  @Transactional
  public void resumeTrip(String tripId) {
    Instant resumedAt = Instant.now();
    Trip trip = tripRepository.findByIdOrThrow(tripId);
    trip.resume();
    pauseRepository.closeOpenPauseIfExists(tripId, resumedAt);
    tripRepository.save(trip);
    setTripState(tripId, TripStatus.IN_PROGRESS);

    auditEvent(tripId, TripEventType.RESUMED, null, null, resumedAt);
    log.info("trip resumed tripId={}", tripId);
  }

  /**
   * Driver POSTs /trips/{id}/end → transition to COMPLETED + fare calculated.
   *
   * FSM rule: if the trip is currently PAUSED, the open pause is closed (resumedAt = endedAt)
   * before fare calculation so that wait time is correctly included.
   */
  @Transactional
  public FareBreakdown endTrip(String tripId, double distanceKm) {
    if (distanceKm <= 0) {
      throw new IllegalArgumentException("distanceKm must be positive, was: " + distanceKm);
    }
    Trip trip = tripRepository.findByIdOrThrow(tripId);
    Instant endedAt = Instant.now();

    pauseRepository.closeOpenPauseIfExists(tripId, endedAt);
    List<TripPause> pauses = pauseRepository.findByTripId(tripId);

    FareBreakdown fare = fareCalculator.calculate(trip, pauses, distanceKm, endedAt);
    trip.complete(distanceKm, fare, endedAt);
    tripRepository.save(trip);

    redisTemplate.delete("trip:state:" + tripId);
    redisTemplate.delete("driver:current-trip:" + trip.getDriverId());
    redisTemplate.delete("trip:surge:" + tripId);

    auditEvent(tripId, TripEventType.COMPLETED, null, null, endedAt);

    long durationSec = Duration.between(trip.getStartedAt(), endedAt).toSeconds();
    long pauseSec    = pauses.stream().mapToLong(p ->
        Duration.between(p.getPausedAt(),
            p.getResumedAt() != null ? p.getResumedAt() : endedAt).toSeconds()
    ).sum();

    TripEndedEvent.FarePayload farePayload = new TripEndedEvent.FarePayload(
        fare.base(), fare.distanceCharge(), fare.timeCharge(), fare.waitCharge(),
        fare.surgeMultiplier(), fare.subtotal(), fare.total(), fare.currency()
    );
    TripEndedEvent tripEndedEvent = TripEndedEvent.create(
        trip.getTripId(), trip.getRideId(),
        trip.getDriverId(), trip.getRiderId(),
        trip.getRegionId(),
        farePayload, distanceKm, durationSec, pauseSec,
        trip.getPaymentMethod(), endedAt
    );
    kafkaTemplate.send("trip.ended", trip.getTripId(), tripEndedEvent);

    NotificationRequestedEvent completedNotif = NotificationRequestedEvent.create(
        trip.getRiderId(),
        List.of("PUSH"),
        "TRIP_COMPLETED",
        Map.of("tripId", tripId, "fare", fare.total().toPlainString(), "currency", fare.currency()));
    kafkaTemplate.send("notification.requested", trip.getRiderId(), completedNotif);
    log.info("trip ended tripId={} tier={} fare={}", tripId, trip.getTier(), fare.total());
    return fare;
  }

  /**
   * POST /trips/{id}/cancel
   *
   * Cancellation fee applies when the trip is already IN_PROGRESS or PAUSED.
   * No fee is charged for MATCHED cancellations (driver hasn't arrived yet).
   */
  @Transactional
  public BigDecimal cancelTrip(String tripId) {
    Trip trip = tripRepository.findByIdOrThrow(tripId);
    boolean wasActive = trip.getStatus() == TripStatus.IN_PROGRESS
                     || trip.getStatus() == TripStatus.PAUSED;

    Instant cancelledAt = Instant.now();

    if (trip.getStatus() == TripStatus.PAUSED) {
      pauseRepository.closeOpenPauseIfExists(tripId, cancelledAt);
    }

    trip.cancel(cancelledAt);
    tripRepository.save(trip);

    redisTemplate.delete("trip:state:" + tripId);
    redisTemplate.delete("driver:current-trip:" + trip.getDriverId());
    redisTemplate.delete("trip:surge:" + tripId);

    auditEvent(tripId, TripEventType.CANCELLED, null, null, cancelledAt);

    BigDecimal fee = wasActive ? cancellationFeeForTier(trip.getTier()) : BigDecimal.ZERO;

    if (fee.compareTo(BigDecimal.ZERO) > 0) {
      PaymentInitiatedEvent paymentEvent = PaymentInitiatedEvent.create(
          UUID.randomUUID().toString(),
          tripId, trip.getRiderId(),
          fee, "INR",
          trip.getPaymentMethod(),
          "cancel-fee-" + tripId
      );
      kafkaTemplate.send("payment.initiated", trip.getTripId(), paymentEvent);
    }

    NotificationRequestedEvent cancelledNotif = NotificationRequestedEvent.create(
        trip.getRiderId(),
        List.of("PUSH"),
        "TRIP_CANCELLED",
        Map.of("tripId", tripId, "cancellationFee", fee.toPlainString()));
    kafkaTemplate.send("notification.requested", trip.getRiderId(), cancelledNotif);
    log.info("trip cancelled tripId={} fee={}", tripId, fee);
    return fee;
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /** Returns the cancellation fee for an active (IN_PROGRESS or PAUSED) trip by tier. */
  private static BigDecimal cancellationFeeForTier(RideTier tier) {
    return switch (tier) {
      case AUTO     -> new BigDecimal("20.00");
      case STANDARD -> new BigDecimal("30.00");
      case PREMIUM  -> new BigDecimal("50.00");
    };
  }

  private void setTripState(String tripId, TripStatus status) {
    redisTemplate.opsForValue().set("trip:state:" + tripId, status.name());
  }

  private void auditEvent(String tripId, TripEventType type,
                          Double lat, Double lon, Instant occurredAt) {
    TripEvent tripEvent = new TripEvent(
        UUID.randomUUID().toString(), tripId, type, lat, lon, occurredAt
    );
    eventRepository.save(tripEvent);
  }
}

