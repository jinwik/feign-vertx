package feign.vertx;

import feign.FeignException;
import feign.Logger;
import feign.Request;
import feign.VertxFeign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import feign.vertx.testcase.IcecreamServiceApi;
import feign.vertx.testcase.IcecreamServiceApiBroken;
import feign.vertx.testcase.domain.Bill;
import feign.vertx.testcase.domain.Flavor;
import feign.vertx.testcase.domain.IceCreamOrder;
import feign.vertx.testcase.domain.Mixin;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static feign.vertx.testcase.domain.Flavor.FLAVORS_JSON;
import static feign.vertx.testcase.domain.Mixin.MIXINS_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("FeignVertx client")
public class VertxHttpClientTest extends AbstractFeignVertxTest {

  @Nested
  @DisplayName("When make a GET request")
  class WhenMakeGetRequest {
    IcecreamServiceApi client;

    @BeforeEach
    void createClient(Vertx vertx) {
      client = VertxFeign
          .builder()
          .vertx(vertx)
          .decoder(new JacksonDecoder(TestUtils.MAPPER))
          .options(new Request.Options(5L, TimeUnit.SECONDS, 5L, TimeUnit.SECONDS, true))
          .logger(new Slf4jLogger())
          .logLevel(Logger.Level.FULL)
          .target(IcecreamServiceApi.class, wireMock.baseUrl());
    }

    @Test
    @DisplayName("will get flavors")
    void testWillGetFlavors(VertxTestContext testContext) {

      /* Given */
      wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
          .withHeader("Accept", equalTo("application/json"))
          .willReturn(aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(FLAVORS_JSON)));

      /* When */
      Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();

      /* Then */
      flavorsFuture.onComplete(res -> testContext.verify(() -> {
        if (res.succeeded()) {
          Collection<Flavor> flavors = res.result();

          assertThat(flavors)
              .hasSize(Flavor.values().length)
              .containsAll(Arrays.asList(Flavor.values()));
          testContext.completeNow();
        } else {
          testContext.failNow(res.cause());
        }
      }));
    }

    @Test
    @DisplayName("will get mixins")
    void testWillGetMixins(VertxTestContext testContext) {

      /* Given */
      wireMock.stubFor(get(urlEqualTo("/icecream/mixins"))
          .withHeader("Accept", equalTo("application/json"))
          .willReturn(aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(MIXINS_JSON)));

      /* When */
      Future<Collection<Mixin>> mixinsFuture = client.getAvailableMixins();

      /* Then */
      mixinsFuture.onComplete(res -> testContext.verify(() -> {
        if (res.succeeded()) {
          Collection<Mixin> mixins = res.result();

          assertThat(mixins)
              .hasSize(Mixin.values().length)
              .containsAll(Arrays.asList(Mixin.values()));
          testContext.completeNow();
        } else {
          testContext.failNow(res.cause());
        }
      }));
    }

    @Test
    @DisplayName("will get order by id")
    void testWillGetOrderById(VertxTestContext testContext) {

      /* Given */
      IceCreamOrder order = generator.generate();
      int orderId = order.getId();
      String orderStr = TestUtils.encodeAsJsonString(order);

      wireMock.stubFor(get(urlEqualTo("/icecream/orders/" + orderId))
          .withHeader("Accept", equalTo("application/json"))
          .willReturn(aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(orderStr)));

      /* When */
      Future<IceCreamOrder> orderFuture = client.findOrder(orderId);

      /* Then */
      orderFuture.onComplete(res -> testContext.verify(() -> {
        if (res.succeeded()) {
          assertThat(res.result())
              .isEqualTo(order);
          testContext.completeNow();
        } else {
          testContext.failNow(res.cause());
        }
      }));
    }

    @Test
    @DisplayName("will return 404 when try to get non-existing order by id")
    void testWillReturn404WhenTryToGetNonExistingOrderById(VertxTestContext testContext) {

      /* Given */
      wireMock.stubFor(get(urlEqualTo("/icecream/orders/123"))
          .withHeader("Accept", equalTo("application/json"))
          .willReturn(aResponse().withStatus(404)));

      /* When */
      Future<IceCreamOrder> orderFuture = client.findOrder(123);

      /* Then */
      orderFuture.onComplete(res -> testContext.verify(() -> {
        if (res.failed()) {
          assertThat(res.cause())
              .isInstanceOf(FeignException.class)
              .hasMessageContaining("404 Not Found");
          testContext.completeNow();
        } else {
          testContext.failNow(new IllegalStateException("FeignException excepted but not occurred"));
        }
      }));
    }
  }

  @Nested
  @DisplayName("When make a POST request")
  class WhenMakePostRequest {
    IcecreamServiceApi client;

    @BeforeEach
    void createClient(Vertx vertx) {
      client = VertxFeign
          .builder()
          .vertx(vertx)
          .encoder(new JacksonEncoder(TestUtils.MAPPER))
          .decoder(new JacksonDecoder(TestUtils.MAPPER))
          .target(IcecreamServiceApi.class, wireMock.baseUrl());
    }

    @Test
    @DisplayName("will make an order")
    void testWillMakeOrder(VertxTestContext testContext) {

      /* Given */
      IceCreamOrder order = generator.generate();
      Bill bill = Bill.makeBill(order);
      String orderStr = TestUtils.encodeAsJsonString(order);
      String billStr = TestUtils.encodeAsJsonString(bill);

      wireMock.stubFor(post(urlEqualTo("/icecream/orders"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("Accept", equalTo("application/json"))
          .withRequestBody(equalToJson(orderStr))
          .willReturn(aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(billStr)));

      /* When */
      Future<Bill> billFuture = client.makeOrder(order);

      /* Then */
      billFuture.onComplete(res -> testContext.verify(() -> {
        if (res.succeeded()) {
          assertThat(res.result())
              .isEqualTo(bill);
          testContext.completeNow();
        } else {
          testContext.failNow(res.cause());
        }
      }));
    }

    @Test
    @DisplayName("will pay bill")
    void testWillPayBill(VertxTestContext testContext) {

      /* Given */
      Bill bill = Bill.makeBill(generator.generate());
      String billStr = TestUtils.encodeAsJsonString(bill);

      wireMock.stubFor(post(urlEqualTo("/icecream/bills/pay"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withRequestBody(equalToJson(billStr))
          .willReturn(aResponse()
              .withStatus(200)));

      /* When */
      Future<Void> payedFuture = client.payBill(bill);

      /* Then */
      payedFuture.onComplete(res -> testContext.verify(() -> {
        if (res.succeeded()) {
          testContext.completeNow();
        } else {
          testContext.failNow(res.cause());
        }
      }));
    }
  }

  @Nested
  @DisplayName("Should fail client instantiation")
  class ShouldFailedClientInstantiation {

    @Test
    @DisplayName("when Vertx is not provided")
    void testWhenVertxMissing() {

      /* Given */
      ThrowableAssert.ThrowingCallable instantiateContractForgottenVertx = () -> VertxFeign
          .builder()
          .target(IcecreamServiceApi.class, wireMock.baseUrl());

      /* Then */
      assertThatCode(instantiateContractForgottenVertx)
          .isInstanceOf(NullPointerException.class)
          .hasMessage("Vertx instance wasn't provided in VertxFeign builder");
    }

    @Test
    @DisplayName("when try to instantiate contract that have method that not return future")
    void testWhenTryToInstantiateBrokenContract(Vertx vertx) {

      /* Given */
      ThrowableAssert.ThrowingCallable instantiateBrokenContract = () -> VertxFeign
          .builder()
          .vertx(vertx)
          .target(IcecreamServiceApiBroken.class, wireMock.baseUrl());

      /* Then */
      assertThatCode(instantiateBrokenContract)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("IcecreamServiceApiBroken#findOrder(int)");
    }
  }

  @Nested
  @DisplayName("Should fail by request timeout")
  class ShouldFailedByRequestTimeout {

    IcecreamServiceApi client;

    @BeforeEach
    void createClient(Vertx vertx) {
      client = VertxFeign
          .builder()
          .vertx(vertx)
          .decoder(new JacksonDecoder(TestUtils.MAPPER))
          .timeout(1000)
          .options(new HttpClientOptions().setLogActivity(true))
          .logger(new Slf4jLogger())
          .logLevel(Logger.Level.FULL)
          .target(IcecreamServiceApi.class, wireMock.baseUrl());
    }

    @Test
    @DisplayName(("when timeout is reached"))
    void testWhenTimeoutIsReached(VertxTestContext testContext) {
      /* Given */
      wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
          .withHeader("Accept", equalTo("application/json"))
          .willReturn(aResponse()
              .withStatus(200)
              .withFixedDelay(1500)
              .withHeader("Content-Type", "application/json")
              .withBody(FLAVORS_JSON)));

      Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();

      /* Then */
      flavorsFuture.onComplete(res -> testContext.verify(() -> {
        if (res.succeeded()) {
          testContext.failNow("should timeout!");
        } else {
          assertThat(res.cause()).isInstanceOf(TimeoutException.class);
          testContext.completeNow();
        }
      }));
    }

    @Test
    @DisplayName(("when timeout is not reached"))
    void testWhenTimeoutIsNotReached(VertxTestContext testContext) {
      /* Given */
      wireMock.stubFor(get(urlEqualTo("/icecream/flavors"))
          .withHeader("Accept", equalTo("application/json"))
          .willReturn(aResponse()
              .withStatus(200)
              .withFixedDelay(100)
              .withHeader("Content-Type", "application/json")
              .withBody(FLAVORS_JSON)));

      Future<Collection<Flavor>> flavorsFuture = client.getAvailableFlavors();

      /* Then */
      flavorsFuture.onComplete(res -> testContext.verify(() -> {
        if (res.succeeded()) {
          Collection<Flavor> flavors = res.result();
          assertThat(flavors)
              .hasSize(Flavor.values().length)
              .containsAll(Arrays.asList(Flavor.values()));
          testContext.completeNow();
        } else {
          testContext.failNow(res.cause());
        }
      }));
    }
  }
}
