# P2P Network

## Features

- Support multiple privacy overlay networks (Tor, I2P) as well as clear net
- Support multiple network identities (e.g. maker use for each offer a dedicated network node and its address)
- Dos protection (e.g. PoW)
- Confidential end to end messaging (encryption and signing keys are decoupled from network identity)
- Peer discovery (seed nodes, handling of persisted peers, peer exchange protocol)
- Peer group management (using a gossip network topology)
- Distributed data management with different data handling policies
- Capability concept for supporting different node types and for adding new features
- Persistence

## Architecture

### Node

- `SocketFactory`
    - Interface for getting a `ServerSocket` and a `Socket` from different implementations (Tor, I2P, ClearNet)
    - Provides factory method for creating the `SocketFactory` for the given `NetworkType`
- `Server`
    - Listening for connections
- `Connection`
    - Listening for messages
    - Send a `Message` by wrapping it into a `MisqMessage`
    - Unwrap `MisqMessage` and pass `Message` to listener
- `Node`
    - Manages `Server` and `Connection` instances
    - Performs initial _Connection Handshake Protocol_
    - Listen on messages from connection and performs _Authorization Protocol_. If successful notify `MessageListener`s
    - At requests to send a message add AuthorizationToken according to _Authorization Protocol_


#### Service layer

- `P2pServiceNode`
    - Creates `Node`
    - Creates `OverlayNetworkService` if that service is supported
    - Creates `ConfidentialMessageService` if that service is supported
    - Creates `DataService` if that service is supported
    - Creates `RelayService` if that service is supported
- `P2pService`
    - Creates `P2pServiceNode`
    - Maintains a map of `P2pServiceNode`s by `NetworkType`

#### Module API

- `NetworkService`
    - Top level service for accessing `P2pService` and `HttpService`

### Services

- `ConfidentialMessageService`
    - Encrypts and signs a `Message` and creates a `ConfidentialMessage`
    - Decrypts and verify signature when receiving a `ConfidentialMessage` and send `Message` to listeners.
    - Forward messages to all it's nodes
    - Listen on messages of all its nodes

- `RelayService`
  TODO

- `DataService`
  TODO

- `OverlayNetworkService`
  TODO

### Basic data structures

- `NetworkType`: Enum with values`TOR, I2P, CLEAR`
- `Address`: Holds `host` and `port`
- `NodeId`: Holds serverPort, a set of supported `NetworkType`s and an ID
- `NetworkId`: Holds supported addresses with related networkType and pubkey. Has `Map<NetworkType, Address>`
  , `PublicKey` and a `tag` field for optimizing key lookup for decryption.
- `NetworkConfig`: Holds selected `NetworkType`, `PeerConfig`, `NodeId` and `baseDirPath` used for Tor and I2P directory
  paths.

### Messages

#### Node level

- `Massege`: Base type of all messages
- `MisqMessage`: Wrapper for messages at `Connection` level
- `Connection.Request`, `Connection.Response`: Used in _Connection Handshake Protocol_
- `AuthorizedMessage`: Used in _Authorization Protocol_

#### ConfidentialMessageService

- `ConfidentialMessage`: Holds `ConfidentialData` and `tag`

### Options/Configs

- `P2pService.Option` TODO

### Protocols

#### Connection Handshake Protocol

At the first connection we perform a handshake protocol for exchanging the nodes capabilities. Basic capabilities are the set of supported
network types and the announced own address which is sent by the initiating node. This is not a verified address but is
useful for most cases to avoid creating a new outbound connection in case there is already an inbound connection to that
node. Further capabilities will be pow related parameters and supported services.
The initial messages require as well to pass the Authorization Protocol using default parameters as nodes parameters are only known after capability exchange.

The initiator of the connection starts the protocol with sending a Connection.Request.

1. Send Connection.Request with own Capability and AuthorizationToken
2. On receiving the Connection.Request send back the Connection.Response with own Capability and AuthorizationToken. Apply capability
   and complete protocol.
3. On receiving the Connection.Response check if AuthorizationToken matches requirement and if peers address matches the address used to send the request. If matches apply capability and complete protocol.

#### Authorization Protocol

Wraps a message into a AuthorizedMessage with adding the AuthorizationToken, which can contain in case of PoW the pow hash and the
related parameters to allow verification. Verifies received messages by unwrapping the AuthorizedMessage using the specific
AuthorizationToken for verification. PoW not implemented yet.

## Config examples

### Node supporting all services and all privacy overlay networks

A typical config contains one Tor and one I2P `P2pServiceNode` with all services activated. First we start the servers
of all our nodes. Then we bootstrap all nodes to the overlay network. After that our node is complete. When sending a
message we create an outbound connection if no connection to that peer already exists. After connection is established
it performs the _Connection Handshake Protocol_ and once completed it is used to send the message. We send the message
from all our network nodes matching the NetworkTypes of the peers `NetworkId`. E.g. if peer also supports both Tor and
one I2P we send the message over both those networkNodes. We can have multiple servers at one `Node` used for multiple
networkIds (e.g. multiple offers). We use a default serverId used for the `OverlayNetworkService`.

### Node just supporting the overlay network and data distribution

This node has not enabled the `ConfidentialMessageService` as it is not used as a user agent node but only for
propagating data in the overlay network.




