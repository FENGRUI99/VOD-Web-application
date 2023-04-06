### 概述

实现了一个小视频网站(miniYouTube)。用户可以通过浏览器访问我们的网站，并根据我们的建议或搜索查看文本、图片或视频等文档。 

- 前端处理与浏览器的交互。正确地接收和处理来自浏览器的HTTP GET请求，并适当地响应200,206,404,500。 

- 后端是基于P2P架构的分布式系统。在同一个局域网中的不同主机上设置多个后端服务器。不同的主机可能有不同的文件，或者多个主机可能有共同的文件。

### 项目1

在您的第一个项目中，您应该实现系统的前端组件，它可以通过web浏览器与用户交互。 


- 基于TCP套接字的通信 

- 接收HTTP GET请求 

- 如果请求头中没有Range字段，则响应200 

- 如果在请求头中有一个Range字段，那么响应206 ->视频从浏览器中查找 

- 如果没有找到文件，则响应404 

- 创建线程池处理并发性，支持至少100个用户同时访问




### 项目2

在这个项目中，您将实现一个后端子系统，它利用UDP之上的自定义传输协议。传输协议的目的是在对等节点之间可靠地传输内容。

- 基于UDP socket的通信

- 带宽限制带宽限制

- **流量控制** ->滑动窗口，丢包重传，最大允许**50%丢包率**
- **拥塞控制** ->动态改变窗口大小
- content chunking->多个节点具有相同的文件，并同时从多个节点请求该文件的不同部分
—1秒内快速响应大文件



### 项目3

在这个项目中，我们将探索底层网络系统，它形成了我们的P2P网络。您将为网络实现一个简单的链路状态路由协议。然后，我们可以使用获得的距离度量来提高我们的运输效率。

- **广播算法**实现路由表
- 动态路由表，每隔一段时间检测邻居是否上线或下线
- **Dijkstra**实现单源最短路径。节点是根据最短路径来请求文件的一部分。请求是成比例的。



### 项目4

留给我们系统的唯一任务是执行一个网络范围的内容搜索。

- Gossip协议实现文件搜索并返回文件的所有节点uuid

- Partial search返回所有部分文件，文件名包含target和拥有该文件的节点uuid
—内容Portal显示首页节点内容文件夹中的所有文件，按查看时间/最新进行排序