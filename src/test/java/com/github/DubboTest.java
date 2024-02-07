package com.github;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.entity.Const;
import com.github.fieldintercept.entity.Department;
import com.github.netty.StartupServer;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.servlet.ServletContext;
import com.github.securityfilter.DubboWebRequestIdCreateFilter;
import com.github.securityfilter.WebSecurityAccessFilter;
import com.github.securityfilter.util.AccessUserUtil;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.AsyncContext;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.util.Assert;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class DubboTest {
    static String ZOOKEEPER_ADDRESS = "zookeeper://192.168.20.35:2181";
    static TokenService tokenService;
    static ReturnFieldDispatchAop<?> fieldDispatchAop = ReturnFieldDispatchAop.newInstance(Const.BEAN_FACTORY);

    int port;
    StartupServer server;

    public DubboTest(int port) {
        this.port = port;
        server = new StartupServer(port);
        server.addProtocol(newHttpServletProtocol());
        server.start();
    }

    public static void main(String[] args) throws IOException {
        List<DubboTest> list = new ArrayList<>();
        for (int port = 85; port < 90; port++) {
            list.add(new DubboTest(port));
        }

        list.forEach(e -> e.server.getBootstrapFuture().syncUninterruptibly());

        for (DubboTest httpTest : list) {
            String u1rl = "http://localhost:" + httpTest.port + "/assertUserEquals?access_token=" + 1;
            String b1 = new String(readInputToBytes(openConnection(u1rl, 1000, 2000).getInputStream()));

            new Thread(() -> {
                while (true) {
                    for (int token = 0; token < 5; token++) {
                        try {
                            String url = "http://localhost:" + httpTest.port + "/assertUserEquals?access_token=" + token;
                            String b = new String(readInputToBytes(openConnection(url, 1000, 2000).getInputStream()));
                            Assert.isTrue("true".equals(b), "assertUserEquals:" + b);
                            Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));
                        } catch (Exception e) {
                            e.printStackTrace();
//                            return;
                        }
                    }
                }
            }).start();
        }
    }

    public static URLConnection openConnection(String urlStr, int connectTimeout, int readTimeout) throws IOException {
        URL url = new URL(urlStr);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
        return connection;
    }

    public static byte[] readInputToBytes(InputStream inputStream) throws IOException {
        int bufferSize = Math.max(inputStream.available(), 8192);
        byte[] buffer = new byte[bufferSize];
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(bufferSize)) {
            int num = inputStream.read(buffer);
            while (num != -1) {
                baos.write(buffer, 0, num);
                num = inputStream.read(buffer);
            }
            baos.flush();
            return baos.toByteArray();
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                //skip
            }
        }
    }

    static {
        ReferenceConfig<TokenService> reference = new ReferenceConfig<>();
        reference.setInterface(TokenService.class);
        reference.setCheck(false);
        reference.setInjvm(false);

        ServiceConfig<TokenService> service = new ServiceConfig<>();
        service.setInterface(TokenService.class);
        service.setRef(new TokenServiceImpl());

        DubboBootstrap.getInstance()
                .application("dubbo-consumer")
                .registry(new RegistryConfig(ZOOKEEPER_ADDRESS))
                .protocol(new ProtocolConfig("dubbo", -1))
                .reference(reference)
                .service(service)
                .start();
        tokenService = reference.get();
    }

    public interface TokenService {
        String selectUserIdByToken(String token);

        Map<String, String> selectUserByUserId(String id);

        boolean assertUserEquals(Map<String, Object> user);
    }

    public static class TokenServiceImpl implements TokenService {
        @Override
        public String selectUserIdByToken(String token) {
            return token;
        }

        @Override
        public Map selectUserByUserId(String id) {
            Map<String, Object> user = new HashMap<>();
            user.put("tenantId", id);
            user.put("id", id);
            user.put("auto", new Department());


//            asyncContext.write();
            fieldDispatchAop.autowiredFieldValue(user);
            return user;
        }

        @Override
        public boolean assertUserEquals(Map<String, Object> user) {
            AsyncContext asyncContext = RpcContext.startAsync();
            Object rootAccessUser = AccessUserUtil.getRootAccessUser(true);

            String s = tokenService.selectUserIdByToken("123");

            AccessUserUtil.runOnAccessUser(1, () -> {
                if (!Objects.equals(AccessUserUtil.getAccessUser(), 1)) {
                    throw new IllegalArgumentException();
                }
                AccessUserUtil.runOnAccessUser(2, () -> {
                    if (!Objects.equals(AccessUserUtil.getAccessUser(), 2)) {
                        throw new IllegalArgumentException();
                    }
                    AccessUserUtil.runOnAccessUser(3, () -> {
                        if (!Objects.equals(AccessUserUtil.getAccessUser(), 3)) {
                            throw new IllegalArgumentException();
                        }
                        AccessUserUtil.runOnRootAccessUser(() -> {
                            if (!Objects.equals(AccessUserUtil.getAccessUser(), rootAccessUser)) {
                                throw new IllegalArgumentException();
                            }
                            AccessUserUtil.runOnAccessUser(4, () -> {
                                if (!Objects.equals(AccessUserUtil.getAccessUser(), 4)) {
                                    throw new IllegalArgumentException();
                                }
                                AccessUserUtil.runOnRootAccessUser(() -> {
                                    if (!Objects.equals(AccessUserUtil.getAccessUser(), rootAccessUser)) {
                                        throw new IllegalArgumentException();
                                    }
                                    AccessUserUtil.runOnRootAccessUser(() -> {
                                        if (!Objects.equals(AccessUserUtil.getAccessUser(), rootAccessUser)) {
                                            throw new IllegalArgumentException();
                                        }
                                    });
                                });
                            });
                        });
                    });
                });
            });

            executor.execute(new DubboAbstractMonitor(() -> {
                Map<String, Object> accessUser1 = AccessUserUtil.getAccessUserMapIfExist();
                Object id1 = accessUser1.get("id");
                Object id2 = user.get("id");

                Object rootAccessUser1 = AccessUserUtil.getRootAccessUser(true);

                AccessUserUtil.runOnAttribute("id", null, () -> {
                    Object rootAccessUser2 = AccessUserUtil.getRootAccessUser(true);

                    Map<String, Object> accessUserMap = AccessUserUtil.getAccessUserMap();
                    if (!Objects.equals(accessUserMap.get("id"), null)) {
                        throw new IllegalArgumentException();
                    }
                });

                boolean equals = Objects.equals(id1, id2);
                if (!equals) {
                    throw new IllegalArgumentException();
                }
                asyncContext.write(equals);
            }));
            return false;
        }
    }

    public static class WebAbstractMonitor implements Runnable {
        private final Runnable runnable;

        public WebAbstractMonitor(Runnable runnable) {
            this.runnable = AccessUserUtil.runnable(runnable);
        }

        @Override
        public void run() {
            runnable.run();
        }
    }


    public static class DubboAbstractMonitor implements Runnable {
        private final Runnable runnable;

        public DubboAbstractMonitor(Runnable runnable) {
            this.runnable = AccessUserUtil.runnable(runnable);
        }

        @Override
        public void run() {
            runnable.run();
        }
    }

    static Executor executor = Executors.newFixedThreadPool(50);

    private HttpServletProtocol newHttpServletProtocol() {
        ServletContext servletContext = new ServletContext();

        servletContext.addFilter("WebSecurityAccessFilter", new WebSecurityAccessFilter<String, Map<String, String>>() {

            @Override
            protected String selectUserId(HttpServletRequest request, String accessToken) {
                return tokenService.selectUserIdByToken(accessToken);
            }

            @Override
            protected Map<String, String> selectUser(HttpServletRequest request, String id, String accessToken) {
                Map stringStringMap = tokenService.selectUserByUserId(id);
                stringStringMap.put("root", true);
                return stringStringMap;
            }
        }).addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "*");

        servletContext.addServlet("HttpServlet", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                        Map<String, Object> accessUserIfExist = AccessUserUtil.getAccessUserMapIfExist();
                        javax.servlet.AsyncContext asyncContext = req.startAsync();
                        executor.execute(new WebAbstractMonitor(() -> {
                            Map user = new HashMap(accessUserIfExist);
                            user.put("runOnAccessUser", "a");
                            AccessUserUtil.runOnAccessUser(user, () -> {
                                boolean b = tokenService.assertUserEquals(accessUserIfExist);
                                try {
                                    resp.getWriter().write(String.valueOf(b));

                                    asyncContext.complete();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                        }));
                    }
                })
                .addMapping("/assertUserEquals");
        servletContext.addFilter("DubboWebRequestIdCreateFilter", new DubboWebRequestIdCreateFilter());
        return new HttpServletProtocol(servletContext);
    }
}
