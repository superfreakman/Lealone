/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.test.vertx;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.lealone.test.UnitTestBase;
import org.lealone.test.vertx.impl.HelloWorldServiceExecuter;
import org.lealone.test.vertx.impl.UserServiceExecuter;
import org.lealone.test.vertx.services.HelloWorldService;
import org.lealone.test.vertx.services.User;
import org.lealone.test.vertx.services.UserService;
import org.lealone.vertx.ServiceExecuterManager;
import org.lealone.vertx.SockJSSocketServiceHandler;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;

public class ServicesTest extends UnitTestBase {

    private static final String url = "jdbc:lealone:embed:test;" //
            + "user=root;password=root;" //
            // + "genServiceCode=true;serviceCodePath=./src/test/java"//
            + "persistent=false;"; //

    public static void main(String[] args) {
        new ServicesTest().run();
    }

    public void run() {
        createServices();
        testBackendRpcServices(); // 在后端执行RPC
        testFrontendRpcServices(); // 在前端执行RPC
    }

    private void createServices() {
        System.out.println("create services");
        try (Connection conn = DriverManager.getConnection(url); Statement stmt = conn.createStatement()) {
            System.out.println("create ALIAS");
            stmt.executeUpdate("CREATE ALIAS executeServiceNoReturnValue for " //
                    + "\"org.lealone.vertx.ServiceExecuterManager.executeServiceNoReturnValue\"");
            stmt.executeUpdate("CREATE ALIAS executeServiceWithReturnValue for " //
                    + "\"org.lealone.vertx.ServiceExecuterManager.executeServiceWithReturnValue\"");

            System.out.println("create table");
            // 创建表: user
            stmt.executeUpdate("create table user(id long, name char(10), notes varchar, phone int)");

            // 创建服务: user_service
            stmt.executeUpdate("create service if not exists user_service (" //
                    + "             add(user user) user," // 第一个user是参数名，第二个user是参数类型，第三个user是返回值类型
                    + "             find(id long) user," //
                    + "             update(user user) boolean," //
                    + "             delete(id long) boolean," //
                    + "         ) package 'org.lealone.test.vertx.generated'" //
                    + "           implement by 'org.lealone.test.vertx.impl.UserServiceImpl'");

            // 创建服务: hello_service
            stmt.executeUpdate("create service hello_service (" //
                    + "             say_hello(name varchar) void" //
                    + "         ) package 'org.lealone.test.vertx.generated'" //
                    + "           implement by 'org.lealone.test.vertx.impl.HelloServiceImpl'");

        } catch (SQLException e) {
            e.printStackTrace();
        }

        ServiceExecuterManager.registerServiceExecuter("hello_world_service", new HelloWorldServiceExecuter());
        ServiceExecuterManager.registerServiceExecuter("user_service", new UserServiceExecuter());
    }

    private void testBackendRpcServices() {
        System.out.println("test backend rpc services");
        HelloWorldService helloWorldService = HelloWorldService.create(url);
        helloWorldService.sayHello();

        UserService userService = UserService.create(url);
        User user = new User().setName("rob");
        user = userService.add(user);
        System.out.println("user.id=" + user.getId());
        user = userService.find(user.getId());

        // user.setPhone(12345678);
        // userService.update(user);
        //
        // userService.delete(1);
    }

    private void testFrontendRpcServices() {
        startHttpServer();
    }

    // http://localhost:8080/index.html
    private static void startHttpServer() {
        VertxOptions opt = new VertxOptions();
        opt.setBlockedThreadCheckInterval(Integer.MAX_VALUE);
        Vertx vertx = Vertx.vertx(opt);
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // router.route().handler(CorsHandler.create("*").allowedMethod(HttpMethod.GET).allowedMethod(HttpMethod.POST));
        setSockJSHandler(vertx, router);
        // 放在最后
        setStaticHandler(vertx, router);

        server.requestHandler(router::accept).listen(8080, res -> {
            if (res.succeeded()) {
                System.out.println("Server is now listening on actual port: " + server.actualPort());
            } else {
                System.out.println("Failed to bind!");
            }
        });
    }

    private static void setStaticHandler(Vertx vertx, Router router) {
        StaticHandler sh = StaticHandler.create("./src/test/resources/webroot/");
        sh.setCachingEnabled(false);
        router.route("/*").handler(sh);
    }

    private static void setSockJSHandler(Vertx vertx, Router router) {
        SockJSHandlerOptions options = new SockJSHandlerOptions().setHeartbeatInterval(2000);
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx, options);
        sockJSHandler.socketHandler(new SockJSSocketServiceHandler());
        router.route("/api/*").handler(sockJSHandler);
    }

}
