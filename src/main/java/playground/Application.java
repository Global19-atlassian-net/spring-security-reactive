/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package playground;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.codec.support.ByteBufferEncoder;
import org.springframework.core.codec.support.JacksonJsonEncoder;
import org.springframework.core.codec.support.JsonObjectEncoder;
import org.springframework.core.codec.support.StringEncoder;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.convert.support.ReactiveStreamsToCompletableFutureConverter;
import org.springframework.core.convert.support.ReactiveStreamsToRxJava1Converter;
import org.springframework.core.io.buffer.DataBufferAllocator;
import org.springframework.core.io.buffer.DefaultDataBufferAllocator;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.boot.HttpServer;
import org.springframework.http.server.reactive.boot.ReactorHttpServer;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.ResponseStatusExceptionHandler;
import org.springframework.web.reactive.handler.SimpleHandlerResultHandler;
import org.springframework.web.reactive.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.reactive.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.reactive.method.annotation.ResponseBodyResultHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import playground.security.HttpBasicFilter;

/**
 * @author Sebastien Deleuze
 */
@Configuration
@PropertySource("classpath:application.properties")
public class Application {

	public static void main(String[] args) throws Exception {
		HttpHandler httpHandler = createHttpHandler();

		HttpServer server = new ReactorHttpServer();
		server.setPort(8080);
		server.setHandler(httpHandler);
		server.afterPropertiesSet();
		server.start();

		CompletableFuture<Void> stop = new CompletableFuture<>();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			stop.complete(null);
		}));
		synchronized (stop) {
			stop.wait();
		}
	}

	public static HttpHandler createHttpHandler() throws IOException {
		Properties prop = new Properties();
		prop.load(Application.class.getClassLoader().getResourceAsStream("application.properties"));

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext("playground");
		DispatcherHandler dispatcherHandler = new DispatcherHandler();
		dispatcherHandler.setApplicationContext(context);

		return WebHttpHandlerBuilder.webHandler(dispatcherHandler)
				.exceptionHandlers(new ResponseStatusExceptionHandler())
				.filters(new HttpBasicFilter())
				.build();
	}

	@Bean
	DataBufferAllocator bufferAllocator() {
		return new DefaultDataBufferAllocator();
	}

	@Bean
	RequestMappingHandlerMapping handlerMapping() {
		return new RequestMappingHandlerMapping();
	}

	@Bean
	RequestMappingHandlerAdapter handlerAdapter() {
		RequestMappingHandlerAdapter handlerAdapter = new RequestMappingHandlerAdapter();
		handlerAdapter.setConversionService(conversionService());
		return handlerAdapter;
	}

	@Bean
	ConversionService conversionService() {
		GenericConversionService service = new GenericConversionService();
		service.addConverter(new ReactiveStreamsToCompletableFutureConverter());
		service.addConverter(new ReactiveStreamsToRxJava1Converter());
		return service;
	}

	@Bean
	ResponseBodyResultHandler responseBodyResultHandler(DataBufferAllocator bufferAllocator) {
		return new ResponseBodyResultHandler(Arrays.asList(
				new ByteBufferEncoder(bufferAllocator), new StringEncoder(bufferAllocator),
				new JacksonJsonEncoder(bufferAllocator, new JsonObjectEncoder(bufferAllocator))), conversionService());
	}

	@Bean
	SimpleHandlerResultHandler simpleHandlerResultHandler() {
		return new SimpleHandlerResultHandler(conversionService());
	}

	@Bean
	public PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

}
