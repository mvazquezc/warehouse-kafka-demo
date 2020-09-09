package io.famartin.warehouse;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.reactivestreams.Publisher;

import io.famartin.warehouse.common.OrderRecord;
import io.famartin.warehouse.common.StockRecord;
import io.quarkus.runtime.StartupEvent;
import io.reactivex.Flowable;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@Path("/warehouse")
public class WarehouseResource {

    @Inject
    @Channel("processed-orders")
    Multi<OrderRecord> orders;

    @Inject
    @Channel("events-sink")
    Multi<JsonObject> events;

    @Inject
    StocksService stocks;

    Multi<String> getPingStream() {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(10))
                .onItem().apply(x -> "{}");
    }

    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Publisher<String> events() {
        return Multi.createBy().merging()
        .streams(
            events.map(b -> b.encode()),
            getPingStream()
        );
        // return Flowable.fromPublisher(events).map(JsonObject::encode);
    }

    private Jsonb json = JsonbBuilder.create();

    @GET
    @Path("/orders")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Publisher<String> orders() {
        return Multi.createBy().merging()
        .streams(
            orders.map(b -> json.toJson(b)),
            getPingStream()
        );
        // return Flowable.fromPublisher(orders).map(JsonObject::encode);
    }

    @POST
    @Path("/stocks")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<StockRecord> addStock(StockRecord request) {
        return stocks.addStock(UUID.randomUUID().toString(), request.getItemId(), request.getQuantity());
    }

}