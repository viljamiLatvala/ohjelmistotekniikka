/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package csgotuc.ui;

import csgotuc.dao.Database;
import csgotuc.dao.ItemDao;
import csgotuc.dao.ItemFetchingService;
import csgotuc.dao.SQLItemDao;
import csgotuc.domain.Item;
import csgotuc.domain.ItemService;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javax.swing.text.html.HTML;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author latvavil
 */
public class Gui extends Application {

    private ItemService itemService;
    private ObservableList<PieChart.Data> pieChartData;
    private ImageView itemPreview;
    private PieChart pieChart;

    @Override
    public void init() {
        try {
            File database = new File("./database.db");
            if (!database.exists()) {
                FileUtils.copyURLToFile(
                        new URL("https://github.com/viljamiLatvala/ohjelmistotekniikka/raw/master/database.db"),
                        new File("./database.db"));
            }
            Database db = new Database("jdbc:sqlite:database.db");
            ItemDao itemDao = new SQLItemDao(db);
            if (itemDao.getAll().isEmpty()) {
                ItemFetchingService itemFetchingService = new ItemFetchingService(itemDao);
                itemFetchingService.fetchAllItems();
            }

            itemService = new ItemService(itemDao);
        } catch (IOException | ClassNotFoundException | SQLException e) {
            System.out.println(e);
        }

        pieChartData = FXCollections.observableArrayList();
    }

    @Override
    public void start(Stage primaryStage) throws SQLException {
        this.itemPreview = new ImageView();
        AnchorPane rootPane = new AnchorPane();

        TilePane tilePane = new TilePane();
        tilePane.setVgap(4);
        tilePane.setHgap(4);
        tilePane.setPrefColumns(4);
        for (int i = 0; i < 10; i++) {
            Group itemGroup = new Group(new Rectangle(100, 100, Color.GRAY));
            tilePane.getChildren().add(itemGroup);
        }

        pieChart = new PieChart();

        ListView<Item> inputListView = new ListView<>();
        ObservableList<Item> testItems = FXCollections.observableArrayList();
        itemService.getPossibleInputs().forEach(item -> testItems.add(item));

        EventHandler<MouseEvent> mouseEnteredHandler = (MouseEvent e) -> {
            Object clickedObject = e.getSource();
            Item item = (Item) ((ListCell) clickedObject).getItem();
            ByteArrayInputStream input = new ByteArrayInputStream(item.getImage());
            Image image = new Image(input);
            itemPreview.setImage(image);
            itemPreview.setX(e.getSceneX());
            itemPreview.setY(e.getSceneY());
            itemPreview.setFitHeight(100);
            itemPreview.setPreserveRatio(true);
        };

        EventHandler<MouseEvent> mouseExitedHandler = (MouseEvent e) -> {
            itemPreview.setImage(null);
        };

        EventHandler<MouseEvent> mouseClickedHandler = (MouseEvent e) -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                Object clickedObject = e.getSource();
                Item item = (Item) ((ListCell) clickedObject).getItem();
                itemService.addToInput(item);

                formInputLoadout(tilePane);
                if (itemService.getInput().size() == 1) {
                    try {
                        formInputOptionList(inputListView, item.getGrade());
                    } catch (SQLException ex) {
                        Logger.getLogger(Gui.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                try {
                    formChart();
                } catch (SQLException ex) {
                    Logger.getLogger(Gui.class.getName()).log(Level.SEVERE, null, ex);
                }
                pieChart.setData(pieChartData);
            }
        };

        inputListView.setCellFactory(item -> {
            return new ListCell<Item>() {
                @Override
                protected void updateItem(Item item, boolean empty) {
                    super.updateItem(item, empty);

                    if (item == null || empty) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item.getName());
                        setOnMouseEntered(mouseEnteredHandler);
                        setOnMouseClicked(mouseClickedHandler);
                        setOnMouseExited(mouseExitedHandler);
                    }
                }
            };
        }
        );
        inputListView.setItems(testItems);

        HBox hbox = new HBox();
        hbox.setPadding(new Insets(10));
        hbox.setSpacing(8);
        hbox.getChildren().add(inputListView);
        hbox.getChildren().add(tilePane);
        hbox.getChildren().add(pieChart);

        rootPane.getChildren().add(hbox);
        rootPane.getChildren().add(itemPreview);

        primaryStage.setScene(new Scene(rootPane));
        primaryStage.setTitle("CSGO Trade-Up Calculator");
        primaryStage.show();
    }

    public void formChart() throws SQLException {
        pieChartData = FXCollections.observableArrayList();

        List<Item> outcomePool = this.itemService.calculateTradeUp();
        int poolSize = outcomePool.size();
        Map<Item, Integer> outcomeDist = new HashMap<>();

        for (int i = 0; i < poolSize; i++) {
            Item curItem = outcomePool.get(i);
            if (outcomeDist.containsKey(curItem)) {
                outcomeDist.put(curItem, outcomeDist.get(curItem) + 1);
            } else {
                outcomeDist.put(curItem, 1);
            }
        }

        pieChartData = FXCollections.observableArrayList();
        outcomeDist.keySet().forEach((item) -> {
            pieChartData.add(new PieChart.Data(item.getName(), outcomeDist.get(item)));
        });

    }

    public void formInputLoadout(TilePane tilePane) {

        List<Item> input = this.itemService.getInput();
        for (int i = 0; i < input.size(); i++) {
            int curIndex = i;
            Item inputItem = this.itemService.getInputItem(i);

            if ( inputItem == null) {
                continue;
            }
            Group newGroup = new Group(new Rectangle(100, 100, Color.DARKSEAGREEN));
            Image image = null;
            image = new Image(new ByteArrayInputStream(inputItem.getImage()));
            ImageView img = new ImageView(image);
            img.setFitHeight(100);
            img.setFitWidth(100);
            img.setPreserveRatio(true);
            double actWidth = img.getBoundsInLocal().getWidth();
            double actHeight = img.getBoundsInLocal().getHeight();
            double xAlignment = newGroup.getLayoutX() + ((100 - actWidth) / 2);
            double yAlignment = newGroup.getLayoutY() + ((100 - actHeight) / 2);
            newGroup.getChildren().add(img);
            newGroup.getChildren().get(1).relocate(xAlignment, yAlignment);

            ContextMenu contextMenu = new ContextMenu();

            MenuItem remove = new MenuItem("Remove");
            remove.setOnAction((ActionEvent event) -> {
                itemService.removeFromInput(curIndex);
                Group itemGroup = new Group(new Rectangle(100, 100, Color.GRAY));
                tilePane.getChildren().remove(curIndex);
                tilePane.getChildren().add(curIndex, itemGroup);
                try {
                    formChart();
                    pieChart.setData(pieChartData);

                } catch (SQLException ex) {
                    Logger.getLogger(Gui.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
            contextMenu.getItems().add(remove);
            newGroup.setOnContextMenuRequested(e -> contextMenu.show(newGroup, e.getScreenX(), e.getScreenY()));
            tilePane.getChildren().set(i, newGroup);

        }
    }

    public void formInputOptionList(ListView list, int grade) throws SQLException {
        ObservableList<Item> newListItems = FXCollections.observableArrayList();
        itemService.getByGrade(grade).forEach(item -> newListItems.add(item));
        list.getItems().setAll(newListItems);
    }

    @Override
    public void stop() {
    }

    public static void main(String[] args) {
        launch(args);
    }
}
