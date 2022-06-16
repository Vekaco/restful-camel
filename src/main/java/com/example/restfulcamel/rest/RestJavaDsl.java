package com.example.restfulcamel.rest;

import com.example.restfulcamel.dto.WeatherDto;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.support.DefaultMessage;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class RestJavaDsl extends RouteBuilder {

    private WeatherDataProvider provider;
    public RestJavaDsl(WeatherDataProvider provider) {
        this.provider = provider;
    }
    //http://localhost:8080/services/javadsl/weather/{city}
    @Override
    public void configure() throws Exception {
        //given default services in application.properties
        //rest:METHOD(get/post/put/delete):apis/uri/
        from("rest:get:javadsl/weather/{city}?produces=application/json")
                .outputType(WeatherDto.class)
                .process(this::getWeatherData);
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
