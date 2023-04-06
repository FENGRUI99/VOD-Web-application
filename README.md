### Overall

The project is a front end separation project. Implemented a small video website (miniYouTube). Users can visit our website through a browser and view documents such as text, pictures or videos based on our recommendations or searches.

- The front end handles the interaction with the browser. Receive and process HTTP GET requests from the browser correctly, and respond 200,206,404,500 as appropriate.

- The back-end is a distributed system based on P2P architecture. Multiple back-end servers are set up on different hosts in the same LAN. Different hosts may have different files, or several hosts may have common files.

### project 1

In your first project, you should implement the front-end component of the system, which can interact with users through a web browser.

- TCP socket based communication
- Receives HTTP GET requests
- response 200 if there is no Range field in the request header
- If there is a Range field in the request header, then response 206 -> video seek from browser
- response 404 if no file is found
- Create a thread pool to handle concurrency and support simultaneous access by at least 100 users

### project 2

In this project, you will implement a back-end subsystem which utilizes a custom transport protocol on top of UDP. The purpose of the transport protocol is to reliably transfer content amongst peer nodes.

- Communication based on UDP sockets
- Bandwidth limit Bandwidth limit
- **Flow control** -> Sliding window, packet loss retransmission, maximum tolerance of **50% packet loss rate**
- **Congestion Control ** -> Dynamically change window size
- content chunking-> Multiple nodes have the same file and request different parts of the file from multiple nodes at the same time
- Respond promptly to large files 1 second

### project 3

In this project, we will explore the underlying network system, which forms our P2P network. You will implement a simple link-state routing protocol for the network. We could then use the obtained distance metric to improve our transport efficiency.

- **broadcast** algorithm implements routing table
- Indicates a dynamic routing table that checks whether neighbors go online or offline at an interval
- **dijkstra** implements a single source shortest path. node is selected according to the shortest path to request part of the file. Requests are proportional

### project 4

The only task left for our system is to perform a network-wide content search.

- Gossip protocol implements file search and returns all node UUids of the file
- partial search returns all partial files with a file name containing target and the node uuid that owns the file
- Content Portal Displays all files in the node content folder in the homepage, which is sorted by view times/latest

