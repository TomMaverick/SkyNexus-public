module skynexus {
    requires javafx.fxml;
    requires java.sql;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign;
    requires org.slf4j;
    requires org.json;
    requires org.kordamp.ikonli.core;
    requires org.controlsfx.controls;
    requires java.desktop;


    opens skynexus to javafx.fxml;
    opens skynexus.controller to javafx.fxml;
    opens skynexus.controller.dialogs to javafx.fxml;
    opens skynexus.controller.factories to javafx.fxml;
    opens skynexus.model to javafx.base;

    exports skynexus;
    exports skynexus.controller;
    exports skynexus.controller.dialogs;
    exports skynexus.controller.factories;
    exports skynexus.database;
    exports skynexus.model;
    opens skynexus.database to javafx.fxml;
    exports skynexus.controller.admin;
    opens skynexus.controller.admin to javafx.fxml;
}
