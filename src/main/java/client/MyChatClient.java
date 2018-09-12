package client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MyChatClient {
    public static void main(String[] args) throws Exception{
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

        try{
            EventLoopGroup workgroup = new NioEventLoopGroup();
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workgroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel sc) throws Exception {
                            sc.pipeline().addLast(new MyChatClientHandler());
                        }
                    });


            ChannelFuture cf1 = bootstrap.connect("192.168.0.21", 8765).sync();


            //标准输入
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

            //利用死循环，不断读取客户端在控制台上的输入内容
            for (;;){
                cf1.channel().writeAndFlush(Unpooled.copiedBuffer((bufferedReader.readLine() +"\r\n").getBytes()));
            }

        }finally {
            eventLoopGroup.shutdownGracefully();
        }
    }
}

//	          1、首先是new 一个ServerBootstrap，看ServerBootstrap的源码，其构造函数中没有什么初始化工作，并且，它是继承AbstractBootstrap类，其中大多数的工作都是其完成的。
//            2、绑定了两个group，这两个group，一个是用来接收到连接请求时，处理，另一个是接收到数据时，处理的工作线程。
//            1. 这里是使用的NioEventLoopGroup，它继承MultithreadEventLoopGroup，
//            在这个NioEventLoopGroup初始化时，会初始化线程池、Selector、ExecutorChooser（PowerOfTwoEventExecutorChooser）、队列拒绝handler、和EventLoop。这里初始化的是NioEventLoop。
//            并初始化一定数量（可以传递参数，或者是CPU线程数*2,或者是1）的NioEventLoop（相当于是线程）
//            2. 而NioEventLoop继承了SingleThreadEventLoop，单线程处理提交的所有任务。主要是初始化任务队列LinkedBlockingQueue。并设置父EventLoop为NioEventLoopGroup。
//            3. 以上，NIOEventLoopGroup初始化完成
//            3、调用channel方法，并设置一个class。这个channel它是AbstractBootstrap的方法，主要功能是：
//            1. 创建一个Channel的生成工厂，ReflectiveChannelFactory，而这个工厂的主要方法是newChannel，生成NioServerSocketChannel，它继承自AbstractNioMessageChannel类。这个channel后面再说。
//            4、 调用childHandler方法，注册一个子的Handler，这个handler，主要是对worker线程使用。这里是new了一个ChannelInitializer类，它其实是继承了ChannelInboundHandlerAdapter。
//            在它的抽象方法中，实现了在ChannelPipeline上注册Handler链。pipeline后面再说。
//            5、第10至16设置TCP参数
//            6、最后，调用bind方法。它也是在AbstractBootstrap中实现的。
//            1. 在bind中，首先对地址和端口封装成SocketAddress，然后调用doBind方法。
//            2. 在doBind中，首先调用的是initAndRegister方法，顾名思义，它就是初始化和注册Selector的。
//            3. 在initAndRegister方法中，首先会初始化一个channel，也就是上面提到的ReflectiveChannelFactory的newChannel方法。生成NioServerSocketChannel。
//            1. 在生成Channel时，使用的是反射的方式。这里着重看一下NioServerSocketChannel的初始化工作。
//            1. 初始化时，先是创建了一个Java原生NIO的ServerSocketChannel，使用的是默认的SelectorProvider，也即调用的是SelectorProvider.provider()方法。
//            2. 初始化AbstractNioMessageChannel，因为它继承了AbstractNioChannel类，所以还会初始化AbstractNioChannel类，而AbstractNioChannel继承了AbstractChannel。
//            下面详细介绍这几个类中初始化的信息。
//            1. 首先在AbstractChannel类中，生成了一个DefaultChannelId，
//            2. 创建了Unsafe类，这个类使用的是newUnsafe方法，这个方法在AbstractNioMessageChannel中实现，生成其私有内部类NioMessageUnsafe，它只有read方法。
//            3. 创建了上文提到的ChannelPipeline，这里创建的是DefaultChannelPipeline，并把自己传递到其中，这里也就是NioServerSocketChannel。
//            在DefaultChannelPipeline中，主要是初始化了HeadContext和TailContext，
//            其中HeadContext继承了AbstractChannelHandlerContext（也就是channelRead的ctx）、ChannelOutboundHandler、ChannelInboundHandler
//            TailContext继承了AbstractChannelHandlerContext、ChannelInboundHandler
//            1. HeadContext的功能：
//            1. 初始化时，把它当成了outbound，因为它被设置成了true。
//            2. 指定AbstractNioMessageChannel的unsafe类
//            3. 其它的功能，在后续的代码分析中体现。
//            2. TailContext的功能：
//            1. 初始化时，把它当成了inbound，因为它被设置成了true。
//
//            4. 设置了接受操作：OP_ACCEPT，设置channel为非阻塞模式。
//            5. 创建了一个NioServerSocketChannelConfig对象，它继承了DefaultServerSocketChannelConfig类，传递进去Java NIO的ServerSocket。
//            1. 会创建一个AdaptiveRecvByteBufAllocator分配策略类。
//            以上，NioServerSocketChannel初始化完成。
//            2. 调用ServerBootstrap的init方法
//            这个方法的主要功能是设置TCP的参数，已经在Pipeline上注册一些handler。
//            1. 获取到DefaultChannelPipeline，在这个Pipeline上注册ChannelInitializer，
//            2. 异步执行在Pipeline上注册了一个ServerBootstrapAcceptor类，这个是ServerBootstrap的内部类，其功能是，用来接受连接的，主要是把由boss线程转移到worker线程
//            并且设置了自动读取。
//            以上初始化完成后，就开始了注册的流程。
//            3. 调用NioEventLoopGroup的register方法
//            1. 通过当前的值%size来获取一个NioEventLoop，最后会在AbstractUnsafe的register方法，并把NioEventLoop传过去。
//            2. 调用AbstractNioChannel的doRegister方法，完成channel的注册。
//            3. invokeHandlerAddedIfNeeded方法完成对handler的添加，因为，每次调用Pipeline的addLast方法时，都会创建一个AddTask，这时候，调用的就是这个TAsk。
//            这时候主要是调用的是在ServerBootstrap中添加的ServerBootstrapAcceptor，也就是添加调用了ChannelInitializer的initChannel方法，最后把ChannelInitializer移除。
//            4. 这时候调用fireChannelRegistered方法，主要是调用HeadContext、TailContext、ServerBootstrapAcceptor等的channelRegistered方法。
//            5. isActive()判断，是否是活的，也就是是否已经绑定成功。这时候还没有做绑定操作，所以是false。
//            以上完成了注册。
//            4. 以上完成后，就进行了doBound操作。initAndRegister方法会返回一个ChannelFuture，判断其是否isDone，如果是true，则进行doBind0操作，这时候才是真正的bound。
//            1. 这个都是异步操作。主要是调用channel的bound，也即NioServerSocketChannel，不过方法在AbstractChannel类中实现，最终调用DefaultChannelPipeline的bind。
//            2. 在Pipeline中，主要是调用tail类的方法，然后找到是outbound的handler也即HeadContext进行bound，然后调用unsafe的bound。
//            3. 这个unsafe是NioMessageUnsafe，不过方法是在AbstractUnsafe类中实现，最终还是调用NioServerSocketChannel的doBind方法。
//            以上，就完成了bound操作。

