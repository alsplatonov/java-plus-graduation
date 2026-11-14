package ru.practicum.ewm.stats.analyzer.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.ewm.stats.analyzer.service.RecommendationService;
import ru.practicum.ewm.stats.analyzer.service.RecommendationService.Scored;
import ru.practicum.ewm.stats.grpc.dashboard.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.grpc.recommendation.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.grpc.recommendation.RecommendedEventProto;
import ru.practicum.ewm.stats.grpc.recommendation.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.grpc.recommendation.UserPredictionsRequestProto;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RecommendationsGrpcService extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {

    private final RecommendationService service;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        respond(responseObserver,
                () -> service.getRecommendationsForUser(request.getUserId(), request.getMaxResults()));
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request,
                                 StreamObserver<RecommendedEventProto> responseObserver) {
        respond(responseObserver,
                () -> service.getSimilarEvents(request.getEventId(), request.getUserId(), request.getMaxResults()));
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        respond(responseObserver, () -> service.getInteractionsCount(request.getEventIdList()));
    }

    private void respond(StreamObserver<RecommendedEventProto> observer, Supplier<List<Scored>> source) {
        try {
            for (Scored item : source.get()) {
                observer.onNext(RecommendedEventProto.newBuilder()
                        .setEventId(item.eventId())
                        .setScore(item.score())
                        .build());
            }
            observer.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка при обработке gRPC-запроса рекомендаций", e);
            observer.onError(Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
        }
    }
}
