/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package network.misq.monitor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import network.misq.common.data.Triple;
import network.misq.common.timer.UserThread;
import network.misq.common.util.CompletableFutureUtils;
import network.misq.desktop.utils.UITimer;
import network.misq.network.NetworkService;
import network.misq.network.p2p.ServiceNode;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.transport.Transport;
import network.misq.network.p2p.services.mesh.MeshService;
import network.misq.network.p2p.services.mesh.monitor.NetworkMonitor;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class NetworkMonitorUI extends Application {
    private static final Logger log = LoggerFactory.getLogger(NetworkMonitorUI.class);
    private NetworkMonitor networkMonitor;
    private FlowPane seedsPane, nodesPane;
    private int nodeWidth;
    private Mode mode = Mode.START_NODES;
    private TextArea infoTextArea;
    private final List<NetworkService> seedNetworkServices = new ArrayList<>();
    private final List<NetworkService> nodeNetworkServices = new ArrayList<>();
    private StringProperty numSeeds = new SimpleStringProperty();
    private StringProperty numNodes = new SimpleStringProperty();

    enum Mode {
        STARTS_SEEDS,
        START_NODES

    }

    @Override
    public void start(Stage primaryStage) {
        UserThread.setExecutor(Platform::runLater);
        UserThread.setTimerClass(UITimer.class);

        networkMonitor = new NetworkMonitor();
        numSeeds.set(String.valueOf(networkMonitor.getNumSeeds()));
        numSeeds.addListener((observable, oldValue, newValue) -> networkMonitor.setNumSeeds(Integer.parseInt(newValue)));
        numNodes.set(String.valueOf(networkMonitor.getNumNodes()));
        numNodes.addListener((observable, oldValue, newValue) -> networkMonitor.setNumNodes(Integer.parseInt(newValue)));

        String bgStyle = "-fx-background-color: #dadada";
        String seedStyle = "-fx-background-color: #80afa1";

        Insets bgPadding = new Insets(10, 10, 10, 10);
        Insets labelPadding = new Insets(4, -20, 0, 0);
        nodeWidth = 50;
        double stageWidth = 2000;
        double availableWidth = stageWidth - 2 * bgPadding.getLeft() - 2 * bgPadding.getRight();
        int nodesPerRow = (int) (availableWidth / nodeWidth);
        stageWidth = nodesPerRow * nodeWidth + 2 * bgPadding.getLeft() + 2 * bgPadding.getRight();

        seedsPane = new FlowPane();
        seedsPane.setStyle(seedStyle);
        seedsPane.setPadding(bgPadding);

        nodesPane = new FlowPane();
        nodesPane.setStyle(bgStyle);
        nodesPane.setPadding(bgPadding);

        HBox modesBox = new HBox(20);
        modesBox.setStyle(bgStyle);
        modesBox.setPadding(bgPadding);

        Label modeLabel = new Label("Mode: ");
        modeLabel.setPadding(labelPadding);
        modesBox.getChildren().add(modeLabel);

        ToggleGroup modesGroup = new ToggleGroup();

        ToggleButton startSeedsButton = new ToggleButton("Seeds");
        startSeedsButton.setOnAction(e -> onMode(Mode.STARTS_SEEDS));
        startSeedsButton.setToggleGroup(modesGroup);
        modesBox.getChildren().add(startSeedsButton);
        modesGroup.selectToggle(startSeedsButton);

        ToggleButton startNodesButton = new ToggleButton("Nodes");
        startNodesButton.setOnAction(e -> onMode(Mode.START_NODES));
        startNodesButton.setToggleGroup(modesGroup);
        modesBox.getChildren().add(startNodesButton);

       /* ToggleButton meshButton = new ToggleButton("Mesh");
        meshButton.setOnAction(e -> onMode(Mode.START_MESH));
        meshButton.setToggleGroup(modesGroup);
        modesBox.getChildren().add(meshButton);*/


      /*  Triple<Label, TextField, HBox> numSeedsTriple = getTextInput("Num seeds:");
        numSeedsTriple.second().textProperty().bindBidirectional(numSeeds);

        Triple<Label, TextField, HBox> numNodesTriple = getTextInput("Num nodes:");
        numNodesTriple.second().textProperty().bindBidirectional(numNodes);*/

      /*  VBox modesWithFieldsBox = new VBox(20);
        modesWithFieldsBox.getChildren().addAll(modesBox, numSeedsTriple.third(), numNodesTriple.third());*/

        HBox actionBox = new HBox(20);
        actionBox.setStyle(bgStyle);
        actionBox.setPadding(bgPadding);

        Label actionLabel = new Label("Info: ");
        actionLabel.setPadding(labelPadding);
        actionBox.getChildren().add(actionLabel);

        ToggleGroup startStopGroup = new ToggleGroup();

       /* ToggleButton startButton = new ToggleButton("Start");
        startButton.setOnAction(e -> onStart());
        startButton.setToggleGroup(startStopGroup);
        actionBox.getChildren().add(startButton);

        ToggleButton stopButton = new ToggleButton("Stop");
        stopButton.setOnAction(e -> onStop());
        stopButton.setToggleGroup(startStopGroup);
        actionBox.getChildren().add(stopButton);*/

        HBox infoBox = new HBox(20);
        infoBox.setStyle(bgStyle);
        infoBox.setPadding(bgPadding);

        Label infoLabel = new Label("Node info: ");
        infoLabel.setPadding(labelPadding);
        infoBox.getChildren().add(actionLabel);

        infoTextArea = new TextArea();
        infoTextArea.setMinHeight(300);
        infoBox.getChildren().add(infoTextArea);

        VBox vBox = new VBox(20);
        vBox.setPadding(bgPadding);
        vBox.getChildren().addAll(seedsPane, nodesPane, infoBox/*, modesWithFieldsBox, actionBox*/);


        ScrollPane scrollPane = new ScrollPane(vBox);
        scrollPane.setFitToWidth(true);

        Scene scene = new Scene(scrollPane, stageWidth, 1400);
        primaryStage.setTitle("Network simulator");
        primaryStage.setScene(scene);
        primaryStage.show();

        onMode(Mode.STARTS_SEEDS);
        onStart();
    }

    private Triple<Label, TextField, HBox> getTextInput(String title) {
        HBox hBox = new HBox(20);
        Label label = new Label(title);
        TextField textField = new TextField();
        hBox.getChildren().addAll(label, textField);
        return new Triple<>(label, textField, hBox);
    }

    private CompletableFuture<Boolean> bootstrap(List<Address> addresses, String name, List<NetworkService> sink, Pane pane) {
        UserThread.execute(() -> stopNodes(sink, pane));
        
        List<CompletableFuture<Boolean>> allFutures = new ArrayList<>();
        addresses.forEach(seedAddress -> {
            int port = seedAddress.getPort();
            NetworkService networkService = networkMonitor.createNetworkService();
            allFutures.add(networkService.bootstrap(port));

            UserThread.execute(() -> {
                Button button = new Button(name + port);
                button.setMinWidth(100);
                button.setMaxWidth(button.getMinWidth());
                pane.getChildren().add(button);
                button.setUserData(networkService);
                button.setOnAction(e -> onNodeInfo(networkService));
                sink.add(networkService);
            });
        });

        return CompletableFutureUtils.allOf(allFutures)
                .thenApply(success -> success.stream().allMatch(e -> e))
                .orTimeout(120, TimeUnit.SECONDS)
                .thenCompose(CompletableFuture::completedFuture);
    }

    private void stopNodes(List<NetworkService> sink, Pane seedsPane) {
        seedsPane.getChildren().clear();
        sink.forEach(e -> {
            CompletableFuture<Void> shutdown = e.shutdown();
            try {
                shutdown.get();
            } catch (InterruptedException | ExecutionException ignore) {
            }
        });
        sink.clear();
    }

  /*  private CompletableFuture<Boolean> startNodes() {
        stopNodes();
        List<CompletableFuture<Boolean>> allFutures = new ArrayList<>();
        networkMonitor.getNodeAddresses().forEach(address -> {
            int port = address.getPort();
            Button button = new Button("Node " + port);
            button.setMinWidth(100);
            button.setMaxWidth(button.getMinWidth());
            nodesPane.getChildren().add(button);

            NetworkService networkService = networkMonitor.createNetworkService();
            allFutures.add(networkService.bootstrap(port));

            button.setUserData(networkService);
            button.setOnAction(e -> onNodeInfo(networkService));
            networkServices.add(networkService);
        });
        return CompletableFutureUtils.allOf(allFutures)
                .thenApply(success -> success.stream().allMatch(e -> e))
                .orTimeout(120, TimeUnit.SECONDS)
                .thenCompose(CompletableFuture::completedFuture);
    }

    private void stopNodes() {
        nodesPane.getChildren().clear();
        networkServices.forEach(e -> {
            CompletableFuture<Void> shutdown = e.shutdown();
            try {
                shutdown.get();
            } catch (InterruptedException | ExecutionException ignore) {
            }
        });
        networkServices.clear();
    }*/


    private void onNodeInfo(NetworkService networkService) {
        String connectionMatrix = networkService.findServiceNode(Transport.Type.CLEAR_NET)
                .flatMap(ServiceNode::getMeshService)
                .map(MeshService::getPeerGroup).stream()
                .map(PeerGroup::getConnectionMatrix)
                .findAny()
                .orElse("null");
        infoTextArea.setText(connectionMatrix);
    }

    private void onStop() {
        stopNodes(seedNetworkServices, seedsPane);
        stopNodes(nodeNetworkServices, nodesPane);
       /* switch (mode) {
            case STARTS_SEEDS -> {
                this.stopNodes();
            }
            case START_NODES -> {
                stopNodes();
            }
        }*/
    }


    private void onStart() {
        bootstrap(networkMonitor.getSeedAddresses(), "Seed ", seedNetworkServices, seedsPane)
                .thenCompose(res1 -> {
                    onMode(Mode.STARTS_SEEDS);
                    return bootstrap(networkMonitor.getNodeAddresses(), "Node ", nodeNetworkServices, nodesPane);
                }).whenComplete((r, t) -> {
                    log.info("All nodes bootstrapped");
                });
        
       /* switch (mode) {
            case STARTS_SEEDS -> {
                bootstrap(networkMonitor.getSeedAddresses(), "Seed ", seedNetworkServices, seedsPane)
                        .thenCompose(res1 -> {
                            onMode(Mode.STARTS_SEEDS);
                            return bootstrap(networkMonitor.getNodeAddresses(), "Node ", nodeNetworkServices, nodesPane);
                        }).whenComplete((r, t) -> {
                            log.info("All nodes bootstrapped");
                        });
            }
            case START_NODES -> {
                startNodes();
            }
        }*/
    }

    private void onMode(Mode mode) {
        this.mode = mode;
    }
}
