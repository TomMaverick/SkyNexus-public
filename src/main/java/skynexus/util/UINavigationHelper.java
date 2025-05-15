package skynexus.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * Utility-Klasse für die Navigation und das Laden von Views sowie die Verwaltung von Fensterfunktionen.
 */
public final class UINavigationHelper {
    private static final Logger logger = LoggerFactory.getLogger(UINavigationHelper.class);
    private static final String FXML_PREFIX = "/fxml/";
    private static final int RESIZE_MARGIN = 10;

    private static final UINavigationHelper instance = new UINavigationHelper();

    private double xOffset;
    private double yOffset;
    private boolean isResizing;

    private UINavigationHelper() {
    }

    public static UINavigationHelper getInstance() {
        return instance;
    }

    /**
     * Lädt eine FXML-View in einen Container.
     */
    public static <T> T loadView(Pane container, String fxmlPath) {
        if (container == null) {
            logger.error("Container ist null");
            return null;
        }

        String fullPath = normalizeFxmlPath(fxmlPath);
        URL resourceURL = getResourceURL(fullPath);
        if (resourceURL == null) {
            logger.error("FXML-Datei nicht gefunden: {}", fullPath);
            return null;
        }

        try {
            FXMLLoader loader = new FXMLLoader(resourceURL);
            loader.setClassLoader(UINavigationHelper.class.getClassLoader());
            loader.setResources(null);

            Node view = loader.load();
            anchorToParent(view);
            view.setUserData(fullPath);

            container.getChildren().clear();
            container.getChildren().add(view);

            logger.debug("View geladen: {}", fullPath);
            return loader.getController();

        } catch (IOException e) {
            logger.error("Fehler beim Laden der View {}: {}", fullPath, e.getMessage());
            return null;
        } catch (IllegalStateException e) {
            logger.error("Controller-Initialisierungsfehler: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Öffnet eine FXML-View in einem bereitgestellten Stage.
     */
    public static <T> T openInNewWindow(String fxmlPath, String title, Stage stage) {
        String fullPath = normalizeFxmlPath(fxmlPath);

        try {
            FXMLLoader loader = new FXMLLoader(getResourceURL(fullPath));
            Parent root = loader.load();
            T controller = loader.getController();

            stage.setTitle(title);
            Scene scene = new Scene(root);
            stage.setScene(scene);

            logger.debug("View in Fenster geladen: {}", fullPath);
            return controller;
        } catch (IOException e) {
            logger.error("Fehler beim Laden der View: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verankert eine Node an allen Seiten des Eltern-Containers.
     */
    public static void anchorToParent(Node node) {
        if (node == null) {
            return;
        }

        AnchorPane.setTopAnchor(node, 0.0);
        AnchorPane.setRightAnchor(node, 0.0);
        AnchorPane.setBottomAnchor(node, 0.0);
        AnchorPane.setLeftAnchor(node, 0.0);
    }

    /**
     * Normalisiert den FXML-Pfad.
     */
    private static String normalizeFxmlPath(String fxmlPath) {
        if (fxmlPath == null) {
            return "";
        }

        String path = fxmlPath.endsWith(".fxml") ? fxmlPath : fxmlPath + ".fxml";
        return path.startsWith(FXML_PREFIX) ? path : FXML_PREFIX + path;
    }

    /**
     * Holt die URL einer Ressource.
     */
    private static URL getResourceURL(String resourcePath) {
        URL url = UINavigationHelper.class.getResource(resourcePath);
        if (url == null) {
            logger.warn("Ressource nicht gefunden: {}", resourcePath);
        }
        return url;
    }

    /**
     * Macht einen Bereich des Fensters ziehbar
     */
    public void makeDraggable(Stage stage, Node draggableArea) {
        if (stage == null || draggableArea == null) {
            logger.warn("Drag-Funktion nicht möglich: Stage oder Area ist null");
            return;
        }

        draggableArea.setOnMousePressed(event -> {
            xOffset = event.getScreenX() - stage.getX();
            yOffset = event.getScreenY() - stage.getY();
        });

        draggableArea.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }

    /**
     * Ermöglicht das Größenändern eines rahmenlosen Fensters
     */
    public void enableResize(Stage stage) {
        if (stage == null || stage.getScene() == null) {
            logger.warn("Resize-Funktion nicht möglich: Stage oder Scene ist null");
            return;
        }

        Scene scene = stage.getScene();

        scene.setOnMouseMoved(event -> {
            if (isResizeArea(event.getX(), event.getY(), scene)) {
                scene.setCursor(Cursor.SE_RESIZE);
            } else {
                scene.setCursor(Cursor.DEFAULT);
            }
        });

        scene.setOnMousePressed(event -> {
            isResizing = isResizeArea(event.getX(), event.getY(), scene);
            if (isResizing) {
                xOffset = stage.getWidth() - event.getX();
                yOffset = stage.getHeight() - event.getY();
            }
        });

        scene.setOnMouseDragged(event -> {
            if (isResizing) {
                double newWidth = Math.max(stage.getMinWidth(), event.getX() + xOffset);
                double newHeight = Math.max(stage.getMinHeight(), event.getY() + yOffset);
                stage.setWidth(newWidth);
                stage.setHeight(newHeight);
            }
        });

        scene.setOnMouseReleased(event -> {
            isResizing = false;
        });
    }

    /**
     * Prüft, ob die Maus im Größenänderungsbereich ist
     */
    private boolean isResizeArea(double mouseX, double mouseY, Scene scene) {
        return mouseX > scene.getWidth() - RESIZE_MARGIN &&
                mouseY > scene.getHeight() - RESIZE_MARGIN;
    }
}
