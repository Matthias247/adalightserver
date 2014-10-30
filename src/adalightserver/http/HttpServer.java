/*
 * Copyright 2014 Matthias Einwag
 *
 * The author licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package adalightserver.http;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;

import rx.Scheduler;
import rx.Subscription;
import rx.schedulers.Schedulers;
import adalightserver.IController;
import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.SystemPropertyUtil;

public class HttpServer {

    private final IController ledController;
    private final ChannelGroup connections;
    private final ChannelGroup wsConnections;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup clientGroup;
    private final Scheduler scheduler;
    private Channel channel;
    private Subscription stateSub;
    private String lastState = "{}";
	
    public HttpServer(IController ledController) {
        this.bossGroup = new NioEventLoopGroup(1);
        this.clientGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());
        this.scheduler = Schedulers.from(bossGroup);
        this.connections = new DefaultChannelGroup(bossGroup.next());
        this.wsConnections = new DefaultChannelGroup(bossGroup.next());
        
        this.ledController = ledController;
    }

    public void start() {
    	ServerBootstrap bootStrap = new ServerBootstrap();
    	bootStrap.group(bossGroup, clientGroup)
                 .channel(NioServerSocketChannel.class)
                 .childHandler(new ServerInitializer(null));
        
        try {
            channel = bootStrap.bind(8081).sync().channel();
        } catch (InterruptedException e) {}
        
        // Listen to the state and send it to all connected clients
        stateSub = ledController.stateChanged().observeOn(scheduler).subscribe(state -> {
            lastState = state;
            wsConnections.writeAndFlush(makeWebSocketEventFrame("stateChanged", state));
        });
    }
    
    public void stop() {
        try {
            stateSub.unsubscribe();
            
            channel.close().sync();
            connections.close();
            
            bossGroup.shutdownGracefully();
            clientGroup.shutdownGracefully();
        } catch (InterruptedException e) {}
    }
    
    private class ServerInitializer extends ChannelInitializer<SocketChannel> {
        private final SslContext sslCtx;
        
        public ServerInitializer(SslContext sslCtx) {
            this.sslCtx = sslCtx;
        }
        
        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            if (sslCtx != null) {
                pipeline.addLast(sslCtx.newHandler(ch.alloc()));
            }
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(65536));
            pipeline.addLast(new ServerHandler());
        }
    }
    
    private void handleWebSocketFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        // System.out.println("Received: " + frame.text());
        JsonSlurper slurper = new JsonSlurper();
        Object o = slurper.parseText(frame.text());
        if (!(o instanceof Map<?, ?>)) return;
        Map<?, ?> msg = (Map<?, ?>) o;
        if (!msg.containsKey("type")) return;
        Object typeO = msg.get("type");
        if (!(typeO instanceof String)) return;
        String type = (String) typeO;
        
        if (type.equals("rq")) {
            // Received a request
            // Need id, method name and data
            if (!msg.containsKey("id") || !msg.containsKey("method") || !msg.containsKey("data")) return;
            Object idObj = msg.get("id");
            long id;
            if (idObj instanceof Integer) {
                id = (Integer) idObj;
            }
            else if (idObj instanceof Long) {
                id = (Long) idObj;
            }
            else return;
            Object methodObj = msg.get("method");
            if (!(methodObj instanceof String)) return;
            String method = (String) methodObj;
            Object dataObj = msg.get("data");
            Map<?, ?> data;
            if (dataObj instanceof Map<?, ?>) data = (Map<?, ?>) dataObj;
            else data = null;
            handleRequest(ctx, id, method, data);
        }
    }
    
    private TextWebSocketFrame makeWebSocketEventFrame(String eventName, String data) {
        StringBuilder b = new StringBuilder();
        b.append("{\"type\":\"ev\", \"name\":\"")
         .append(eventName)
         .append("\", \"data\":")
         .append(data)
         .append("}");
        return new TextWebSocketFrame(b.toString());
    }
    
    private TextWebSocketFrame makeWebSocketResultMsg(long id, Object result, Object error) {
        JsonBuilder builder = new JsonBuilder();
        Map<String, Object> msg = new HashMap<String, Object>();
        msg.put("type", "rp");
        msg.put("id", id);
        if (error != null) {
            msg.put("error", error);
        } else {
            msg.put("result", result);
        }
        builder.call(msg);
        return new TextWebSocketFrame(builder.toString());
    }
    
    private void handleRequest(ChannelHandlerContext ctx, long id, String method, Map<?, ?> data) {
        if (method.equals("stop")) {
            ledController.stop()
            .thenAccept(v -> ctx.writeAndFlush(makeWebSocketResultMsg(id, null, null)))
            .exceptionally(e -> { 
                ctx.writeAndFlush(makeWebSocketResultMsg(id, null, "Bad request"));
                return null; 
                });
        } else if (method.equals("getState")) {
            bossGroup.execute(() -> 
                ctx.writeAndFlush(makeWebSocketResultMsg(id, lastState, null))
            );
        } else if (method.equals("getCurrentScript")) {
            ledController.getCurrentScript()
            .thenAccept(script -> ctx.writeAndFlush(makeWebSocketResultMsg(id, script, null)))
            .exceptionally(e -> { 
                ctx.writeAndFlush(makeWebSocketResultMsg(id, null, "Bad request"));
                return null; 
                });
        } else if (method.equals("getScripts")) {
            ledController.getAvailableScripts()
            .thenAccept(scripts -> ctx.writeAndFlush(makeWebSocketResultMsg(id, scripts, null)))
            .exceptionally(e -> { 
                ctx.writeAndFlush(makeWebSocketResultMsg(id, null, "Bad request"));
                return null; 
                });
        } else if (method.equals("setScript")) {
            boolean isError = false;
            String scriptName = null;
            Map<String,String> parameters = new HashMap<>();
            
            if (data == null) isError = true;
            if (!isError && !data.containsKey("name") || !data.containsKey("parameters")) isError = true;
            if (!isError) {
                Object nameObj = data.get("name");
                if (!(nameObj instanceof String)) isError = true;
                else scriptName = (String) nameObj;
                
                Object paramMapObj = data.get("parameters");
                if (!(paramMapObj instanceof Map<?,?>)) isError = true;
                else {
                    Map<?,?> pmap = (Map<?,?>) paramMapObj;
                    for (Map.Entry<?, ?> e : pmap.entrySet()) {
                        if (!(e.getKey() instanceof String) || !(e.getValue() instanceof String)) {
                            isError = true;
                            break;
                        } else {
                            parameters.put((String) e.getKey(), (String) e.getValue());
                        }
                    }
                }
            }
            
            if (isError) {
                ctx.writeAndFlush(makeWebSocketResultMsg(id, null, "Bad request"));
                return;
            }
            
            ledController.setScript(scriptName, parameters)
            .thenAccept(script -> ctx.writeAndFlush(makeWebSocketResultMsg(id, script, null)))
            .exceptionally(e -> { 
                ctx.writeAndFlush(makeWebSocketResultMsg(id, null, "Bad request"));
                return null; 
                });
        }
    }
    
    class ServerHandler extends SimpleChannelInboundHandler<Object> {
        boolean isWebSocket = false;
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            connections.add(ctx.channel());
        };
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            connections.remove(ctx.channel());
            wsConnections.remove(ctx.channel());
        };
        
        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object data) {
            if (data instanceof FullHttpRequest) {
                FullHttpRequest req = (FullHttpRequest) data;
                if (!isWebSocket) {
                    handleHttpRequest(ctx, req);
                } else {
                    sendErrorResponse(ctx, req, BAD_REQUEST);
                }
            } else if (data instanceof TextWebSocketFrame) {
                handleWebSocketFrame(ctx, (TextWebSocketFrame) data);
            } else {
                // invalid data
                ctx.close();
            }
        }
        
        private String getWebSocketLocation(ChannelHandlerContext ctx, FullHttpRequest req) {
            String location = req.headers().get(HOST) + req.getUri();
            if (ctx.pipeline().get(SslHandler.class) != null) {
                return "wss://" + location;
            } else {
                return "ws://" + location;
            }
        }
        
        private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
            // Handle a bad request.
            if (!req.getDecoderResult().isSuccess()) {
                sendErrorResponse(ctx, req, BAD_REQUEST);
                return;
            }
            // Allow only GET methods.
            if (req.getMethod() != GET) {
                sendErrorResponse(ctx, req, FORBIDDEN);
                return;
            }
            
            String path = req.getUri();
            System.out.println("Server => Request: " + path);
            try {
                if (path.equals("/ws")) {
                    isWebSocket = true;
                    String wsLocation = getWebSocketLocation(ctx, req);
                    WebSocketServerHandshaker handshaker = new WebSocketServerHandshakerFactory(
                            wsLocation, null, false, 64 * 1024).newHandshaker(req);
                    if (handshaker == null) {
                        WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel())
                                                        .addListener(ChannelFutureListener.CLOSE);
                    } else {
                        handshaker.handshake(ctx.channel(), req);
                        // Push the initial state to the client.
                        // Do it from the server thread were it's safe
                        bossGroup.execute(() -> {
                            ctx.writeAndFlush(makeWebSocketEventFrame("stateChanged", lastState));
                        });
                        wsConnections.add(ctx.channel());
                    }
                } 
                else {
                    handleStaticFileRequest(ctx, req, path);
                }
            } catch (Throwable e) {
                sendErrorResponse(ctx, req, BAD_REQUEST);
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
    
    private static void sendErrorResponse(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status) {
        sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, status));
    }
    
    @SuppressWarnings("unused")
    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, String response) {
        FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK);
        ByteBuf buf = Unpooled.copiedBuffer(response, CharsetUtil.US_ASCII);
        res.content().writeBytes(buf);
        buf.release();
        res.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        HttpHeaders.setContentLength(res, res.content().readableBytes());
        sendHttpResponse(ctx, req, res);
    }
    
    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
        // res.headers().set("Access-Control-Allow-Methods", "POST, OPTIONS, GET");
        // res.headers().set("Access-Control-Allow-Origin", "*");
        // res.headers().set("Access-Control-Allow-Headers", "*");
        
        // Generate an error page if response getStatus code is not OK (200).
        if (res.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
            HttpHeaders.setContentLength(res, res.content().readableBytes());
        }
        // Send the response and close the connection if necessary.
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
    
    private void handleStaticFileRequest(ChannelHandlerContext ctx, FullHttpRequest req, String path) throws Exception {
        
        if (req.getMethod() != HttpMethod.GET) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }
        
        if (path.equals("/")) path = "/index.html";
        
        final String spath = sanitizeUri(path);
        if (spath == null) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }
        File file = new File(spath);
        if (file.isHidden() || !file.exists()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }
        if (file.isDirectory() || !file.isFile() || !file.canRead()) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
            return;
        }

        RandomAccessFile raf = null;
        byte[] content;
        long fileLength;
        try {
            raf = new RandomAccessFile(file, "r");
            fileLength = raf.length();
            if (fileLength > 4 * 1024 * 1024) {
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
                return;
            }
            content = new byte[(int) fileLength];
            raf.read(content);
        } catch (Exception e) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
            return;
        } finally {
            if (raf != null) raf.close();
        }
        
        FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK);
        res.content().writeBytes(content);
        
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        res.headers().set(HttpHeaders.Names.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
        res.headers().set(HttpHeaders.Names.CONTENT_LENGTH, fileLength);
        
        sendHttpResponse(ctx, req, res);
    }
    
    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

    private static String sanitizeUri(String uri) {
        // Decode the path.
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }

        if (!uri.startsWith("/")) {
            return null;
        }

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.') ||
            uri.contains('.' + File.separator) ||
            uri.startsWith(".") || uri.endsWith(".") ||
            INSECURE_URI.matcher(uri).matches()) {
            return null;
        }

        // Convert to absolute path.
        return SystemPropertyUtil.get("user.dir") + File.separator + "static" + File.separator + uri;
    }

}
