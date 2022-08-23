# 两条一样的INSERT语句竟然引发了死锁？

标签： MySQL是怎样运行的

------

两条一样的INSERT语句竟然引发了死锁，这究竟是人性的扭曲，还是道德的沦丧，让我们不禁感叹一句：卧槽！这也能死锁，然后眼中含着悲催的泪水无奈的改起了业务代码。

好的，在深入分析为啥两条一样的INSERT语句也会产生死锁之前，我们先介绍一些基础知识。

## 准备一下环境

为了故事的顺利发展，我们新建一个用了无数次的`hero`表：

```sql
CREATE TABLE hero (
    number INT AUTO_INCREMENT,
    name VARCHAR(100),
    country varchar(100),
    PRIMARY KEY (number),
    UNIQUE KEY uk_name (name)
) Engine=InnoDB CHARSET=utf8;
```

然后向这个表里插入几条记录：

```sql
INSERT INTO hero VALUES
    (1, 'l刘备', '蜀'),
    (3, 'z诸葛亮', '蜀'),
    (8, 'c曹操', '魏'),
    (15, 'x荀彧', '魏'),
    (20, 's孙权', '吴');
```

现在`hero`表就有了两个索引（一个唯一二级索引，一个聚簇索引），示意图如下：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008188.webp)

## INSERT语句如何加锁

读过《MySQL是怎样运行的：从根儿上理解MySQL》的小伙伴肯定知道，INSERT语句在正常执行时是不会生成锁结构的，它是靠聚簇索引记录自带的`trx_id`隐藏列来作为隐式锁来保护记录的。

但是在一些特殊场景下，INSERT语句还是会生成锁结构的，我们列举一下：

### 1. 待插入记录的下一条记录上已经被其他事务加了gap锁时

每插入一条新记录，都需要看一下待插入记录的下一条记录上是否已经被加了gap锁，如果已加gap锁，那INSERT语句应该被阻塞，并生成一个`插入意向锁`。

比方说对于hero表来说，事务T1运行在`REPEATABLE READ`（后续简称为RR，后续也会把READ COMMITTED简称为RC）隔离级别中，执行了下边的语句：

```sql
# 事务T1
mysql> BEGIN;
Query OK, 0 rows affected (0.00 sec)

mysql> SELECT * FROM hero WHERE number < 8 FOR UPDATE;
+--------+------------+---------+
| number | name       | country |
+--------+------------+---------+
|      1 | l刘备      | 蜀      |
|      3 | z诸葛亮    | 蜀      |
+--------+------------+---------+
2 rows in set (0.02 sec)
```

这条语句会对主键值为1、3、8的这3条记录都添加X型`next-key`锁，不信的话我们使用SHOW ENGINE INNODB STATUS语句看一下加锁情况，图中箭头指向的记录就是number值为8的记录：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008229.webp)

> 小贴士：
>
> 至于SELECT、DELETE、UPDATE语句如何加锁，我们已经在之前的文章中分析过了，这里就不再赘述了。

此时事务T2想插入一条主键值为4的聚簇索引记录，那么T2在插入记录前，首先要定位一下主键值为4的聚簇索引记录在页面中的位置，发现主键值为4的下一条记录的主键值是8，而主键值是8的聚簇索引记录已经被添加了gap锁（next-key锁包含了正经记录锁和gap锁），那么事务T2就需要进入阻塞状态，并生成一个类型为`插入意向锁`的锁结构。

我们在事务T2中执行一下INSERT语句验证一下：

```sql
mysql> BEGIN;
Query OK, 0 rows affected (0.00 sec)

mysql> INSERT INTO hero VALUES(4, 'g关羽', '蜀');
```

此时T2进入阻塞状态，我们再使用SHOW ENGINE INNODB STATUS看一下加锁情况：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008226.webp)

可见T2对主键值为8的聚簇索引记录加了一个`插入意向锁`（就是箭头处指向的lock_mode X locks gap before rec insert intention），并且处在waiting状态。

好了，验证过之后，我们再来看看代码里是如何实现的：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008185.webp)

`lock_rec_insert_check_and_lock`函数用于看一下别的事务是否阻止本次INSERT插入，如果是，那么本事务就给被别的事务添加了gap锁的记录生成一个`插入意向锁`，具体过程如下：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008189.webp)

> 小贴士：
>
> lock_rec_other_has_conflicting函数用于检测本次要获取的锁和记录上已有的锁是否有冲突，有兴趣的同学可以看一下。

### 2. 遇到重复键时

如果在插入新记录时，发现页面中已有的记录的主键或者唯一二级索引列与待插入记录的主键或者唯一二级索引列值相同（不过可以有多条记录的唯一二级索引列的值同时为NULL，这里不考虑这种情况了），此时插入新记录的事务会获取页面中已存在的键值相同的记录的锁。

如果是主键值重复，那么：

- 当隔离级别不大于RC时，插入新记录的事务会给已存在的主键值重复的聚簇索引记录添加S型正经记录锁。
- 当隔离级别不小于RR时，插入新记录的事务会给已存在的主键值重复的聚簇索引记录添加S型next-key锁。

如果是唯一二级索引列重复，那**不论是哪个隔离级别，插入新记录的事务都会给已存在的二级索引列值重复的二级索引记录添加S型next-key锁**，再强调一遍，加的是next-key锁！加的是next-key锁！加的是next-key锁！这是rc隔离级别中为数不多的给记录添加gap锁的场景。

> 小贴士：
>
> 本来设计InnoDB的大叔并不想在RC隔离级别引入gap锁，但是由于某些原因，如果不添加gap锁的话，会让唯一二级索引中出现多条唯一二级索引列值相同的记录，这就违背了UNIQUE约束。所以后来设计InnoDB的大叔就很不情愿的在RC隔离级别也引入了gap锁。

我们也来做一个实验，现在假设上边的T1和T2都回滚了，现在将隔离级别调至RC，重新开启事务进行测试。

```sql
mysql> SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
Query OK, 0 rows affected (0.01 sec)

# 事务T1
mysql> BEGIN;
Query OK, 0 rows affected (0.00 sec)

mysql> INSERT INTO hero VALUES(30, 'x荀彧', '魏');
ERROR 1062 (23000): Duplicate entry 'x荀彧' for key 'uk_name'
```

然后执行SHOW ENGINE INNODB STATUS语句看一下T1加了什么锁：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008187.webp)

可以看到即使现在T1的隔离级别为RC，T1仍然给name列值为'x荀彧'的二级索引记录添加了S型next-key锁（图中红框中的lock mode S）。

如果我们的INSERT语句还带有`ON DUPLICATE KEY...` 这样的子句，如果遇到主键值或者唯一二级索引列值重复的情况，会对B+树中已存在的相同键值的记录加X型锁，而不是S型锁（不过具体锁的具体类型是和前面描述一样的）。

好了，又到了看代码求证时间了，我们看一下吧：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008636.webp)

`row_ins_scan_sec_index_for_duplicate`是检测唯一二级索引列值是否重复的函数，具体加锁的代码如下所示：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008667.webp)

如上图所示，在遇到唯一二级索引列重复的情况时：

- 1号红框表示对带有`ON DUPLICATE ...`子句时的处理方案，具体就是添加X型锁。
- 2号红框表示对正常INSERT语句的处理方案，具体就是添加S型锁。

不过不论是那种情况，添加的lock_typed的值都是`LOCK_ORDINARY`，表示next-key锁。

在主键重复时INSERT语句的加锁代码我们就不列举了。

### 3. 外键检查时

当我们向子表中插入记录时，我们分两种情况讨论：

- 当子表中的外键值可以在父表中找到时，那么无论当前事务是什么隔离级别，只需要给父表中对应的记录添加一个`S型正经记录锁`就好了。
- 当子表中的外键值在父表中找不到时：那么如果当前隔离级别不大于RC时，不对父表记录加锁；当隔离级别不小于RR时，对父表中该外键值所在位置的下一条记录添加gap锁。

由于外键不太常用，例子和代码就都不举例了，有兴趣的小伙伴可以打开《MySQL是怎样运行的：从根儿上理解MySQL》查看例子。

## 死锁要出场了

好了，基础知识预习完了，该死锁出场了。

看下边这个平平无奇的INSERT语句：

```sql
INSERT INTO hero(name, country) VALUES('g关羽', '蜀'), ('d邓艾', '魏');
```

这个语句用来插入两条记录，不论是在RC，还是RR隔离级别，如果两个事务并发执行它们是有一定几率触发死锁的。为了稳定复现这个死锁，我们把上边一条语句拆分成两条语句：

```sql
INSERT INTO hero(name, country) VALUES('g关羽', '蜀');
INSERT INTO hero(name, country) VALUES('d邓艾', '魏');
```

拆分前和拆分后起到的作用是相同的，只不过拆分后我们可以人为的控制插入记录的时机。如果T1和T2的执行顺序是这样的：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008669.webp)

也就是：

- T1先插入name值为`g关羽`的记录，可以插入成功，此时对应的唯一二级索引记录被`隐式锁`保护，我们执行SHOW ENGINE INNODB STATUS语句，发现啥一个行锁（row lock）都没有（因为SHOW ENGINE INNODB STATUS不显示隐式锁）：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008671.webp)

- 接着T2也插入name值为`g关羽`的记录。由于T1已经插入name值为`g关羽`的记录，所以T2在插入二级索引记录时会遇到重复的唯一二级索引列值，此时T2想获取一个S型next-key锁，但是T1并未提交，T1插入的name值为`g关羽`的记录上的隐式锁相当于一个X型正经记录锁（RC隔离级别），所以T2向获取S型next-key锁时会遇到锁冲突，T2进入阻塞状态，并且将T1的隐式锁转换为显式锁（就是帮助T1生成一个正经记录锁的锁结构）。这时我们再执行SHOW ENGINE INNODB STATUS语句：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008774.webp)

可见，T1持有的name值为`g关羽`的隐式锁已经被转换为显式锁（X型正经记录锁，lock_mode X locks rec but not gap）；T2正在等待获取一个S型next-key锁（lock mode S waiting）。

- 接着T1再插入一条name值为`d邓艾`的记录。在插入一条记录时，会在页面中先定位到这条记录的位置。在插入name值为`d邓艾`的二级索引记录时，发现现在页面中的记录分布情况如下所示：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008732.webp)

很显然，name值为`'d邓艾'`的二级索引记录所在位置的下一条二级索引记录的name值应该是`'g关羽'`（按照汉语拼音排序）。那么在T1插入name值为`d邓艾`的二级索引记录时，就需要看一下name值为`'g关羽'`的二级索引记录上有没有被别的事务加gap锁。

有同学想说：目前只有T2想在name值为`'g关羽'`的二级索引记录上添加`S型next-key锁`（next-key锁包含gap锁），但是T2并没有获取到锁呀，目前正在等待状态。那么T1不是能顺利插入name值为`'g关羽'`的二级索引记录么？

我们看一下执行结果：

```csharp
# 事务T2
mysql> INSERT INTO hero(name, country) VALUES('g关羽', '蜀');
ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction
```

很显然，触发了一个死锁，T2被InnoDB回滚了。

这是为啥呢？T2明明没有获取到name值为`'g关羽'`的二级索引记录上的`S型next-key锁`，为啥T1还不能插入入name值为`d邓艾`的二级索引记录呢？

这我们还得回到代码上来，看一下插入新记录时是如何判断锁是否冲突的：

![img](https://typora-imagehost-1308499275.cos.ap-shanghai.myqcloud.com/2022-8/202208232008107.webp)

看一下画红框的注释，意思是：只要别的事务生成了一个显式的gap锁的锁结构，不论那个事务已经获取到了该锁(granted)，还是正在等待获取（waiting），当前事务的INSERT操作都应该被阻塞。

回到我们的例子中来，就是T2已经在name值为`'g关羽'`的二级索引记录上生成了一个S型next-key锁的锁结构，虽然T2正在阻塞（尚未获取锁），但是T1仍然不能插入name值为`d邓艾`的二级索引记录。

这样也就解释了死锁产生的原因：

- T1在等待T2释放name值为`'g关羽'`的二级索引记录上的gap锁。
- T2在等待T1释放name值为`'g关羽'`的二级索引记录上的X型正经记录锁。

两个事务相互等待对方释放锁，这样死锁也就产生了。

## 怎么解决这个死锁问题？

两个方案：

- 方案一：一个事务中只插入一条记录。
- 方案二：先插入name值为`'d邓艾'`的记录，再插入name值为`'g关羽'`的记录

为啥这两个方案可行？屏幕前的大脑瓜是不是也该转一下分析一波呗~