# Netty核心功能——数据容器（ByteBuf）

>网络数据的基本单位总是字节，Java NIO 提供了 ByteBuffer 作为它的字节容器，但是这个类使用起来过于复杂，而且也有些繁琐。
>Netty 的 ByteBuffer 替代品是 ByteBuf，一个强大的实现，既解决了 JDK API 的局限性，又为网络应用程序的开发者提供了更好的 API。

## 1、简介

Netty 的数据处理 API 通过两个组件暴露——`abstract class ByteBuf` 和 `interface  ByteBufHolder`，下面是一些 ByteBuf API 的优点：

- 它可以被用户自定义的缓冲区类型扩展；
- 通过内置的复合缓冲区类型实现了透明的零拷贝；
- 容量可以按需增长（类似于 JDK 的 StringBuilder）；
- 在读和写这两种模式之间切换不需要调用 ByteBuffer 的 flip()方法；
- 读和写使用了不同的索引；
- 支持方法的链式调用；
- 支持引用计数；
- 支持池化。

对比ByteBuffer的缺点：

- `ByteBuffer`长度固定，一旦分配完成，它的容量不能动态扩展和收缩，当需要编码的POJO对象大于`ByteBuffer`的容量时，会发生索引越界异常；
- `ByteBuffer`只有一个标识位置的指针`position`，读写的时候需要手工调用`flip()`和`rewind()`等，使用者必须小心谨慎地处理这些API，否则很容易导致程序处理失败；
- `ByteBuffer`的API功能有限，一些高级和实用的特性它不支持，需要使用者自己编程实现。

## 2、ByteBuf 类——Netty 的数据容器

所有的网络通信都涉及字节序列的移动，所以高效易用的数据结构明显是必不可少的。所以理解Netty 的 ByteBuf 是如何满足这些需求的很重要。

### 2.1 工作原理

`ByteBuf`工作机制:`ByteBuf`维护了两个不同的索引，一个用于读取，一个用于写入。`readerIndex`和`writerIndex`的初始值都是0，当从`ByteBuf`中读取数据时，它的`readerIndex`将会被递增(它不会超过`writerIndex`)，当向`ByteBuf`写入数据时，它的`writerIndex`会递增。

![image-20221017224234800](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/202210172242866.png)

`ByteBuf`的几个特点：

1. 名称以`readXXX`或者`writeXXX`开头的`ByteBuf`方法，会推进对应的索引，而以`setXXX`或`getXXX`开头的操作不会。
2. 在读取之后，`0～readerIndex`的就被视为`discard`的，调用`discardReadBytes`方法，可以释放这部分空间，它的作用类似`ByteBuffer`的`compact()`方法。
3. `readerIndex`和`writerIndex`之间的数据是可读取的，等价于`ByteBuffer`的`position`和`limit`之间的数据。`writerIndex`和`capacity`之间的空间是可写的，等价于`ByteBuffer`的`limit`和`capacity`之间的可用空间。

### 2.2 ByteBuf的三种类型

#### 堆缓冲区

> 最常用的 ByteBuf 模式是将数据存储在 JVM 的堆空间中。这种模式被称为支撑数组（backing array），它能在没有使用池化的情况下提供快速的分配和释放

`优点`：由于数据存储在JVM的堆中可以快速创建和快速释放，并且提供了数组的直接快速访问的方法。

`缺点`：每次读写数据都要先将数据拷贝到直接缓冲区（相关阅读：[Java NIO 直接缓冲区和非直接缓冲区对比](http://www.codebaoku.com/it-java/it-java-230692.html)）再进行传递。

示例：

```java
// 创建一个堆缓冲区
ByteBuf buffer = Unpooled.buffer(10);
String s = "waylau";
buffer.writeBytes(s.getBytes());

// 检查是否是支撑数组
if (buffer.hasArray()) {

  // 获取支撑数组的引用
  byte[] array = buffer.array();

  // 计算第一个字节的偏移量
  int offset = buffer.readerIndex() + buffer.arrayOffset();

  // 可读字节数
  int length = buffer.readableBytes();
    
  // 使用数组、偏移量和长度作为参数调用自定义的使用方法
  printBuffer(array, offset, length);
}

/**
  * 打印出Buffer的信息
  * 
  * @param buffer
  */
private static void printBuffer(byte[] array, int offset, int len) {
  System.out.println("array：" + array);
  System.out.println("array->String：" + new String(array));
  System.out.println("offset：" + offset);
  System.out.println("len：" + len);
}

/**
输出结果：
array：[B@5b37e0d2
array->String：waylau    
offset：0
len：6
*/
```

#### 直接缓冲区

> Direct Buffer在堆之外直接分配内存，直接缓冲区不会占用堆的容量。

`优点`：在使用Socket传递数据时性能很好，由于数据直接在内存中，不存在从JVM拷贝数据到直接缓冲区的过程，性能好。

`缺点`：因为Direct Buffer是直接在内存中，所以分配内存空间和释放内存比堆缓冲区更复杂和慢。

示例：

```java
// 创建一个直接缓冲区
ByteBuf buffer = Unpooled.directBuffer(10);
String s = "waylau";
buffer.writeBytes(s.getBytes());

// 检查是否是支撑数组.
// 不是支撑数组，则为直接缓冲区
if (!buffer.hasArray()) {

  // 计算第一个字节的偏移量
  int offset = buffer.readerIndex();

  // 可读字节数
  int length = buffer.readableBytes();

  // 获取字节内容
  byte[] array = new byte[length];
  buffer.getBytes(offset, array);
    
  // 使用数组、偏移量和长度作为参数调用自定义的使用方法
  printBuffer(array, offset, length);
}

/**
  * 打印出Buffer的信息
  * 
  * @param buffer
  */
private static void printBuffer(byte[] array, int offset, int len) {
  System.out.println("array：" + array);
  System.out.println("array->String：" + new String(array));
  System.out.println("offset：" + offset);
  System.out.println("len：" + len);
}

/**
输出结果：
array：[B@6d5380c2
array->String：waylau
offset：0
len：6
*/
```

#### 复合缓冲区

>复合缓冲区是 Netty 特有的缓冲区。本质上类似于提供一个或多个 `ByteBuf` 的组合视图，可以根据需要添加和删除不同类型的 `ByteBuf`。
>

`优点`：提供了一种访问方式让使用者自由地组合多个`ByteBuf`，避免了复制和分配新的缓冲区。

`缺点`：不支持访问其支撑数组。因此如果要访问，需要先将内容复制到堆内存中，再进行访问。

示例：

```java
// 创建一个堆缓冲区
ByteBuf heapBuf = Unpooled.buffer(3);
String way = "way";
heapBuf.writeBytes(way.getBytes());

// 创建一个直接缓冲区
ByteBuf directBuf = Unpooled.directBuffer(3);
String lau = "lau";
directBuf.writeBytes(lau.getBytes());

// 创建一个复合缓冲区
CompositeByteBuf compositeBuffer = Unpooled.compositeBuffer(10);
compositeBuffer.addComponents(heapBuf, directBuf); // 将缓冲区添加到符合缓冲区

// 检查是否是支撑数组.
// 不是支撑数组，则为复合缓冲区
if (!compositeBuffer.hasArray()) {

  for (ByteBuf buffer : compositeBuffer) {
    // 计算第一个字节的偏移量
    int offset = buffer.readerIndex();

    // 可读字节数
    int length = buffer.readableBytes();

    // 获取字节内容
    byte[] array = new byte[length];
    buffer.getBytes(offset, array);

    // 使用数组、偏移量和长度作为参数调用自定义的使用方法
    printBuffer(array, offset, length);
  }

}

/**
  * 打印出Buffer的信息
  * 
  * @param buffer
  */
private static void printBuffer(byte[] array, int offset, int len) {
  System.out.println("array：" + array);
  System.out.println("array->String：" + new String(array));
  System.out.println("offset：" + offset);
  System.out.println("len：" + len);
}

/**
输出结果：
array：[B@4d76f3f8
array->String：way
offset：0
len：3
array：[B@2d8e6db6
array->String：lau
offset：0
len：3
*/
```

## 3、字节级操作

> ByteBuf 提供了许多超出基本读、写操作的方法用于修改它的数据。

### 3.1 随机访问索引和顺序访问索引

如同在普通的 Java 字节数组中一样，ByteBuf 的索引是从零开始的：第一个字节的索引是 0，最后一个字节的索引总是 数组容量 - 1。在ByteBuf的实现类中都有一个方法可以快速获得容量值，那就是capacity()。有了这个capcity()方法，我们就能很简单的实现随机访问索引：

```java
for (int i = 0;i<buf.capacity();i++){
    char b = (char)buf.getByte(i);//通过 getBytes 系列接口来对ByteBuf进行随机访问。
    System.out.println(b);
}
```

> Tips: 用getBytes随机访问不会改变readerIndex

![image-20221018162120836](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221018162120836.png)

通过 **`readerIndex()`** 和 **`writerIndex()`** 获取读Index和写Index。

### 3.2 可丢弃字节

在上图中标记为可丢弃字节的分段包含了已经被读过的字节。通过调用 `discardReadBytes`()方法，可以丢弃它们并回收空间。这个分段的初始大小为 0，存储在 `readerIndex` 中， 会随着 read 操作的执行而增加（get操作不会移动 `readerIndex`）。

下图展示了在上图的缓冲区上调用`discardReadBytes`()方法后的结果，需要注意的是丢弃并不是字节把已经读的字段的字节不要了，而是把尚未读的字节数移到最开始。(这样做对可写分段的内容并没有任何的保证，`因为只是移动了可以读取的字节以及 writerIndex，而没有对所有可写入的字节进行擦除写`)

![image-20221018162238643](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221018162238643.png)

### 3.3 可读字节

> `ByteBuf` 的可读字节分段存储了实际数据。新分配的、包装的或者复制的缓冲区的默认的 `readerIndex` 值为 0。任何名称以 `read` 或者 `skip` 开头的操作都将检索或者跳过位于当前 `readerIndex` 之前的数据，并且在`readerIndex` 的基础上增加已读字节数。 如果被调用的方法需要一个 `ByteBuf` 参数作为写入的目标，并且没有指定目标索引参数， 那么该目标缓冲区的 `writerIndex` 也将被增加。

### 3.4 可写字节

> 可写字节分段是指一个拥有未定义内容的、写入就绪的内存区域。新分配的缓冲区的 writerIndex 的默认值为 0。任何名称以 `write` 开头的操作都将从当前的 `writerIndex` 处 开始写数据，并且在`writerIndex` 的基础上增加已写字节数。如果写操作的目标也是 `ByteBuf`，并且没有指定 源索引的值，则源缓冲区的 `readerIndex` 也同样会被增加相同的大小。

### 3.5 索引管理

1. JDK 的 `InputStream` 定义了 `mark(int readlimit)`和 `reset()`方法，这些方法分别 被用来将流中的当前位置标记为指定的值，以及将流重置到该位置。

2. 同样，可以通过调用 `markReaderIndex()`、`markWriterIndex()`、`resetWriterIndex()` 和 `resetReaderIndex()`来标记和重置 `ByteBuf` 的 `readerIndex` 和 `writerIndex`。这些和 `InputStream` 上的调用类似，只是没有 `readlimit` 参数来指定标记什么时候失效

3. 也可以通过调用 `readerIndex(int)`或者 `writerIndex(int)`来将索引移动到指定位置。试 图将任何一个索引设置到一个无效的位置都将导致一个 `IndexOutOfBoundsException`。 

4. 可以通过调用 `clear()`方法来将 `readerIndex` 和 `writerIndex` 都设置为 0。注意，这 并不会清除内存中的内容。调用后的存储结构如下图所示：

   ![image-20221018170542708](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221018170542708.png)

   > 调用 clear()比调用 discardReadBytes()轻量得多，因为它将只是重置索引而不会复 制任何的内存

### 3.6 派生缓冲区

派生缓冲区为 `ByteBuf` 提供了以专门的方式来呈现其内容的视图。这类视图是通过以下方法被创建的：

- duplicate()；
- slice()；
- slice(int, int)；
- Unpooled.unmodifiableBuffer(…)；
- order(ByteOrder)；
- readSlice(int)。

每个这些方法都将返回一个新的 ByteBuf 实例，它具有自己的读索引、写索引和标记 索引。其内部存储和 JDK 的 ByteBuffer 一样也是共享的。这使得派生缓冲区的创建成本是很低廉的，但是这也意味着，如果你修改了它的内容，也同时修改了其对应的源实例，所以要小心。

> ByteBuf 复制 如果需要一个现有缓冲区的真实副本，请使用 copy()或者 copy(int, int)方 法。不同于派生缓冲区，由这个调用所返回的 ByteBuf 拥有独立的数据副本。
>
> ![image-20221018171737952](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221018171737952.png)

### 3.7 读/写操作

有两种类别的读/写操作：

- get()和 set()操作，从给定的索引开始，并且保持索引不变；
- read()和 write()操作，从给定的索引开始，并且会根据已经访问过的字节数对索引进行调整。

常用的 get()方法如下图：

![image-20221019174207098](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221019174207098.png)

常用的 set()方法如下图：

![image-20221019174246152](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221019174246152.png)

常用的 read()方法如下图：

![image-20221019174405140](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221019174405140.png)

常用的 write()方法如下图：

![image-20221019174422075](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221019174422075.png)

## 4、ByteBufHolder 接口

### 4.1 按需分配：ByteBufAllocator 接口

> Netty 中内存分配有一个最顶层的抽象就是ByteBufAllocator，负责分配所有ByteBuf 类型的内存。他的一些操作如下：

![image-20221020134402753](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221020134402753.png)

可以通过 `Channel`（每个都可以有一个不同的 `ByteBufAllocator` 实例）或者绑定到 `ChannelHandler` 的 `ChannelHandlerContext` 获取一个到 `ByteBufAllocator` 的引用，如下图所示：

![image-20221020135103763](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221020135103763.png)

Netty提供了两种`ByteBufAllocator`的实现：`PooledByteBufAllocator`和`UnpooledByteBufAllocator`。前者池化了ByteBuf的实例以提高性能并最大限度地减少内存碎片，这是通过一种 叫做`jemalloc`的方法来分配内存的（阅读资料：[jemalloc剖析](https://wertherzhang.com/jemalloc%E5%89%96%E6%9E%90/)）。后者的实现不池化`ByteBuf`实例，并且在每次它被调用时都会返回一个新的实例。

### 4.2 Unpooled 缓冲区

> 可能某些情况下，你未能获取一个到 ByteBufAllocator 的引用。对于这种情况，Netty 提 供了一个简单的称为 Unpooled 的工具类，它提供了静态的辅助方法来创建未池化的 ByteBuf 实例。他的一些操作如下：

![image-20221020135640610](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-10/image-20221020135640610.png)

### 4.3 ByteBufUtil 类

> `ByteBufUtil` 提供了用于操作 `ByteBuf` 的静态的辅助方法。这个 API 是通用的，并且和池化无关。

## 5、引用计数

> 引用计数是一种通过在某个对象所持有的资源不再被其他对象引用时释放该对象所持有的资源来优化内存使用和性能的技术。`Netty` 在第 4 版中为 `ByteBuf` 和 `ByteBufHolder` 引入了 引用计数技术，它们都实现了 `interface ReferenceCounted`。

### 5.1 基本原理

1. 一个新创建的引用计数对象的初始引用计数是1。

   ```java
   ByteBuf buf = ctx.alloc().directbuffer();
   assert buf.refCnt() == 1;
   ```

2. 当你释放掉引用计数对象，它的引用次数减1.如果一个对象的引用计数到达0，该对象就会被释放或者归还到创建它的对象池。

   ```java
   assert buf.refCnt() == 1;
   // release() returns true only if the reference count becomes 0.
   boolean destroyed = buf.release();
   assert destroyed;
   assert buf.refCnt() == 0;
   ```

3. 访问引用计数为0的引用计数对象会触发一次IllegalReferenceCountException。

   ```java
   assert buf.refCnt() == 0;
   try {
   buf.writeLong(0xdeadbeef);
   throw new Error("should not reach here");
   } catch (IllegalReferenceCountExeception e) {
   // Expected
   }
   ```

4. 只要引用计数对象未被销毁，就可以通过调用retain()方法来增加引用次数。

   ```java
   ByteBuf buf = ctx.alloc().directBuffer();
   assert buf.refCnt() == 1;
   
   buf.retain();
   assert buf.refCnt() == 2;
   
   boolean destroyed = buf.release();
   assert !destroyed;
   assert buf.refCnt() == 1;
   ```

### 5.2 谁来销毁

一般的原则是，最后访问引用计数对象的部分负责对象的销毁。更具体地来说：

- 如果一个[发送]组件要传递一个引用计数对象到另一个[接收]组件，发送组件通常不需要 负责去销毁对象，而是将这个销毁的任务推延到接收组件
- 如果一个组件消费了一个引用计数对象，并且不知道谁会再访问它（例如，不会再将引用 发送到另一个组件），那么，这个组件负责销毁工作。

### 5.3 内存泄漏问题

引用计数的缺点是，引用计数对象容易发生泄露。因为JVM并不知道Netty的引用计数实现，当引用计数对象不 可达时，JVM就会将它们GC掉，即时此时它们的引用计数并不为0。一旦对象被GC就不能再访问，也就不能归还到缓冲池，所以会导致内存泄露。 庆幸的是，尽管发现内存泄露很难，但是Netty会对分配的缓冲区的1%进行采样，来检查你的应用中是否存在内存泄露。

#### 内存泄露检查等级

总共有4个内存泄露检查等级：

- DISABLED – 完全禁用检查。不推荐。
- SIMPLE – 检查1%的缓冲区是否存在内存泄露。默认。
- ADVANCED – 检查1%的缓冲区，并提示发生内存泄露的位置
- PARANOID – 与ADVANCED等级一样，不同的是会检查所有的缓冲区。对于自动化测试很有用，你可以让构建测试失败 如果构建输出中包含’LEAK’ 用JVM选项 `-Dio.netty.leakDetectionLevel` 来指定内存泄露检查等级

#### 避免泄露最佳实践

- 指定SIMPLE和PARANOI等级，运行单元测试和集成测试
- 在将你的应用部署到整个集群前，尽可能地用足够长的时间，使用SIMPLE级别去调试你的程序，来看是否存在内存泄露
- 如果存在内存泄露，使用ADVANCED级别去调试程序，去获取内存泄漏的位置信息
- 不要将存在内存泄漏的应用部署到整个集群
