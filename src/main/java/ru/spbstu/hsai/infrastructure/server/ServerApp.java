package ru.spbstu.hsai.infrastructure.server;

import io.undertow.Undertow;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.UndertowHttpHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import ru.spbstu.hsai.infrastructure.ServerProperties;
import ru.spbstu.hsai.infrastructure.ServerStarter;

@Component
public class ServerApp implements ServerStarter {
    public void start(AnnotationConfigApplicationContext context) {
        HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(context).build();
        UndertowHttpHandlerAdapter adapter = new UndertowHttpHandlerAdapter(httpHandler);
        ServerProperties props = context.getBean(ServerProperties.class);
        Undertow server = Undertow.builder()
                .addHttpListener(props.port(), props.host())
                .setHandler(adapter)
                .build();
        server.start();
    }
}
