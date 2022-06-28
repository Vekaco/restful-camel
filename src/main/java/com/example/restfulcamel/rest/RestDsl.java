package com.example.restfulcamel.rest;

import com.example.restfulcamel.dto.WeatherDto;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.support.DefaultMessage;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class RestDsl extends RouteBuilder {

    public static final String WEATHER_EVENT = "weather-event";
    public static final String RABBIT_URI = "rabbitmq://localhost:5672/amq.direct?queue=%s&routingKey=%s&autoDelete=false";

    private WeatherDataProvider provider;
    public RestDsl(WeatherDataProvider provider) {
        this.provider = provider;
    }

    @Override
    public void configure() throws Exception {

        //beauty json format
        //http://localhost:8080/services/api-doc/
        //http://localhost:8080/services/api-doc/openapi.yaml
        restConfiguration().component("servlet").bindingMode(RestBindingMode.auto)
                .dataFormatProperty("prettyPrint", "true").apiContextPath("api-doc")
                .apiProperty("api.title", "Camel Rest APIs")
                .apiProperty("api.version", "1.0.0")
                .apiContextListing(true);

        //http://localhost:8080/services/weather/{city}
        //rest("apis").{method}("uri")
        rest().consumes(MediaType.APPLICATION_JSON_VALUE).produces(MediaType.APPLICATION_JSON_VALUE)
                .get("/weather/{city}")
                .responseMessage("200", "on good request")
                .responseMessage("404", "For invalid requests")
                .description("get weather data for a given city")
                .param()
                    .name("city").type(RestParamType.path).description("the name fo the city e.g. London")
                    .dataType("String")
                .endParam()
                .outType(WeatherDto.class).to("direct:get-weather-data")
                .post("/weather").responseMessage("201", "when created")
                .description("add weather for a city").type(WeatherDto.class)
                .param()
                    .name("body")
                    .type(RestParamType.body)
                    .description("payload for weather")
                .endParam()
                .to("direct:save-weather-data");
                //.route().process(this::getWeatherData);

        from("direct:get-weather-data")
                .process(this::getWeatherData);

        from("direct:save-weather-data")
                .process(this::saveWeatherData)
                .wireTap("direct:write-to-rabbit");

        from("direct:write-to-rabbit")
                .marshal().json(JsonLibrary.Jackson, WeatherDto.class)
                .toF(RABBIT_URI, WEATHER_EVENT, WEATHER_EVENT);

    }

    private void saveWeatherData(Exchange exchange) {
        WeatherDto body = exchange.getMessage().getBody(WeatherDto.class);
        provider.setCurrentWeather(body);
    }

    private void getWeatherData(Exchange exchange) {
        String city = exchange.getMessage().getHeader("city", String.class);
        WeatherDto currentWeather = provider.getCurrentWeather(city);

        if (Objects.nonNull(currentWeather)) {
            Message message = new DefaultMessage(exchange);
            message.setBody(currentWeather);
            exchange.setMessage(message);
        } else {
            exchange.getMessage().setHeader(HTTP_RESPONSE_CODE, NOT_FOUND.value() );
        }
    }
}
