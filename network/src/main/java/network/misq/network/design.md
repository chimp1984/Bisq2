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

### Nodes

Nodes are structured following the chain of responsibility pattern.

#### Connection layer

- `SocketFactory`
  - Interface for getting a `ServerSocket` and a `Socket` from different implementations (Tor, I2P, ClearNet)
  - Provides factory method for creating the `SocketFactory` for the given `NetworkType`
- `Server`
  - Listening for connections
- `RawConnection`
  - Listening for messages
  - Send a `Message` by wrapping it into a `MisqMessage`
  - Unwrap `MisqMessage` and pass `Message` to listener
- `RawNode`
  - Manages `Server` and `RawConnection` instances

#### Capability exchange layer

- `CapabilityAwareNode`
  - Creates `RawNode`
  - Performs the _Capability Exchange Protocol_
  - Maintains a queue for messages sent while protocol is not completed and process those after completion
  - Creates `Connection` as wrapper for `RawConnection` containing `Capability` as well as other metadata
  - Activates `Connection` and `Message` listeners once protocol is completed

#### Authorization layer

- `AuthorizedNode`
  - Creates `CapabilityAwareNode`
  - Performs _Authorization Protocol_
  - For sending wrap `Message` into a `AuthorizedMessage` via _Authorization Protocol_
  - Expect type `AuthorizedMessage` at message handler, unwraps it and verify it in _Authorization Protocol_

#### Service layer

- `P2pServiceNode`
  - Creates `AuthorizedNode`
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
- `MisqMessage`: Wrapper for messages at `RawConnection` level
- `CapabilityRequest`, `CapabilityResponse`: Used in _Capability Exchange Protocol_
- `AuthorizedMessage`: Used in _Authorization Protocol_

#### ConfidentialMessageService

- `ConfidentialMessage`: Holds `ConfidentialData` and `tag`

### Options/Configs

- `P2pService.Option` TODO

### Protocols

#### Capability Exchange Protocol

At the first connection we perform an exchange of the nodes capabilities. Basic capabilities are the set of supported
network types and the announced own address which is sent by the initiating node. This is not a verified address but is
useful for most cases to avoid creating a new outbound connection in case there is already an inbound connection to that
node. Further capabilities will be pow related parameters and supported services.

The initiator of the connection starts the protocol with sending a CapabilityRequest.

1. Send CapabilityRequest with own Capability and nonce
2. On receiving the CapabilityRequest send back the CapabilityResponse with own Capability and nonce. Apply capability
   and complete protocol.
3. On receiving the CapabilityResponse check if nonce matches and if peers address matches the address used to send the
   request. If matches apply capability and complete protocol.

#### Authorization Protocol

Wraps a message into a GuardedMessage with adding the AccessToken, which can contain in case of PoW the pow hash and the
related parameters to allow verification. Verifies received messages by unwrapping the GuardedMessage using the specific
AccessToken for verification. PoW not implemented yet.

## Config examples

### Node supporting all services and all privacy overlay networks

A typical config contains one Tor and one I2P `P2pServiceNode` with all services activated. First we start the servers
of all our nodes. Then we bootstrap all nodes to the overlay network. After that our node is complete. When sending a
message we create an outbound connection if no connection to that peer already exists. After connection is established
it performs the _Capability Exchange Protocol_ and once completed it is used to send the message. We send the message
from all our network nodes matching the NetworkTypes of the peers `NetworkId`. E.g. if peer also supports both Tor and
one I2P we send the message over both those networkNodes. We can have multiple servers at one `RawNode` used for
multiple networkIds (e.g. multiple offers). We use a default serverId used for the `OverlayNetworkService`.

### Node just supporting the overlay network and data distribution

This node has not enabled the `ConfidentialMessageService` as it is not used as a user agent node but only for
propagating data in the overlay network.




