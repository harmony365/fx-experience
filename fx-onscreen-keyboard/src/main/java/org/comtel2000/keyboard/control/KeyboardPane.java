package org.comtel2000.keyboard.control;

/*******************************************************************************
 * Copyright (c) 2016 comtel2000
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name of the comtel2000 nor the names of its contributors may be used to endorse or
 * promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.comtel2000.keyboard.event.KeyButtonEvent;
import org.comtel2000.keyboard.robot.FXRobotHandler;
import org.comtel2000.keyboard.robot.IRobot;
import org.comtel2000.keyboard.xml.KeyboardLayoutHandler;
import org.comtel2000.keyboard.xml.layout.Keyboard;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

public class KeyboardPane extends Region implements StandardKeyCode, EventHandler<KeyButtonEvent> {

    private final static org.slf4j.Logger logger = LoggerFactory.getLogger(KeyboardPane.class);
    private final static String DEFAULT_CSS = "/css/KeyboardButtonStyle.css";
    private final EnumMap<KeyboardType, Region> typeRegionMap = new EnumMap<>(KeyboardType.class);
    private final List<IRobot> robots = new ArrayList<>();
    private final Map<Locale, String> availableLocales = new LinkedHashMap<>();
    private String _keyBoardStyle;
    private StringProperty keyBoardStyle;
    private boolean _cacheLayout;
    private BooleanProperty cacheLayout;
    private boolean _symbol;
    private BooleanProperty symbol;
    private boolean _shift;
    private BooleanProperty shift;
    private boolean _control;
    private BooleanProperty control;
    private boolean _spaceKeyMove = true;
    private BooleanProperty spaceKeyMove;
    private boolean _capsLock = true;
    private BooleanProperty capsLock;
    private double _scaleOffset = 0.2d;
    private DoubleProperty scaleOffset;
    private double _scale = 1.0d;
    private DoubleProperty scale;
    private double _minScale = 0.7d;
    private DoubleProperty minScale;
    private double _maxScale = 5.0d;
    private DoubleProperty maxScale;
    private DefaultLayer _layer = DefaultLayer.DEFAULT;
    private ObjectProperty<DefaultLayer> layer;
    private Path _layerPath;
    private ObjectProperty<Path> layerPath;
    private Locale _locale = Locale.getDefault();
    private ObjectProperty<Locale> locale;
    private Locale _activeLocale;
    private ObjectProperty<Locale> activeLocale;
    private KeyboardType _activeType;
    private ObjectProperty<KeyboardType> activeType;
    private EventHandler<? super Event> closeEventHandler;
    private double mousePressedX, mousePressedY;
    private KeyboardLayoutHandler handler;

    private EventHandler<MouseEvent> movedHandler;

    private EventHandler<MouseEvent> draggedHandler;

    public KeyboardPane() {
        getStyleClass().add("key-background");
        setFocusTraversable(false);
        URL stylesheet = KeyboardPane.class.getResource(DEFAULT_CSS);
        _keyBoardStyle = stylesheet.toString();

    }

    private static int parseInt(String i) {
        return i.startsWith("0x") ? Integer.parseInt(i.substring(2), 16) : Integer.parseInt(i);
    }

    @Override //(JDK 8u40 or later)
    public String getUserAgentStylesheet() {
        return getKeyBoardStyle();
    }

    public void load() throws IOException, URISyntaxException {

        handler = new KeyboardLayoutHandler(isCacheLayout());

        if (robots.isEmpty()) {
            logger.debug("load default fx robot handler");
            robots.add(new FXRobotHandler());
        }
        getStylesheets().add(getKeyBoardStyle());

        setLayoutLocale(getLocale());
        setKeyboardType(KeyboardType.TEXT);

        if (getScale() != 1.0d) {
            setScaleX(getScale());
            setScaleY(getScale());
        }

        setOnZoom(e -> {
            double s = getScale() * e.getTotalZoomFactor();
            if (s >= getMinScale() && s <= getMaxScale()) {
                setScale(s);
                e.consume();
            }
        });

        setOnScroll(e -> {
            double s = getScale() + (e.getDeltaY() > 0.0d ? getScaleOffset() : -getScaleOffset());
            if (s >= getMinScale() && s <= getMaxScale()) {
                setScale(s);
                e.consume();
            }
        });
    }

    private void setKeyboardType(boolean ctrl, boolean shift, boolean symbol) {
        if (ctrl) {
            setKeyboardType(KeyboardType.CTRL);
            return;
        }
        if (symbol) {
            setKeyboardType(shift ? KeyboardType.SYMBOL_SHIFT : KeyboardType.SYMBOL);
            return;
        }
        setKeyboardType(shift ? KeyboardType.TEXT_SHIFT : KeyboardType.TEXT);
    }

    public void resetLocale() {
        switchLocale(getLocale());
    }

    public void switchLocale(final Locale local) {
        try {
            if (local.equals(getActiveLocale())) {
                return;
            }
            setLayoutLocale(local);
            setActiveType(null);
            setKeyboardType(KeyboardType.TEXT);
        } catch (IOException | URISyntaxException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void switchLayer(final DefaultLayer layer) {
        if (layer.equals(getLayer())) {
            logger.warn("layer already selected");
            return;
        }
        try {
            setLayerPath(null);
            setLayer(layer);
            availableLocales.clear();

            setActiveLocale(null);
            setLayoutLocale(getLocale());
            setActiveType(null);
            setKeyboardType(KeyboardType.TEXT);
        } catch (IOException | URISyntaxException e) {
            logger.error(e.getMessage(), e);
        }

    }

    private void setLayoutLocale(final Locale local) throws IOException, URISyntaxException {

        logger.debug("try to set keyboard local: {}->{}", getActiveLocale(), local);

        Map<Locale, String> localeMap = getAvailableLocales();
        if (localeMap.containsKey(local)) {
            if (local.equals(getActiveLocale())) {
                logger.debug("locale already active: {}", local);
                return;
            }
            setActiveLocale(local);
        } else if (localeMap.containsKey(Locale.forLanguageTag(local.getLanguage()))) {
            if (Locale.forLanguageTag(local.getLanguage()).equals(getActiveLocale())) {
                logger.debug("locale language already active: {}", local);
                return;
            }
            setActiveLocale(Locale.forLanguageTag(local.getLanguage()));
        } else {
            if (Locale.ENGLISH.equals(getActiveLocale())) {
                logger.info("locale language already active: {}", local);
                return;
            }
            setActiveLocale(Locale.ENGLISH);
        }
        logger.debug("use keyboard local: {}", getActiveLocale());
        String root = localeMap.get(getActiveLocale());

        addTypeRegion(KeyboardType.TEXT, root, "kb-layout.xml");
        addTypeRegion(KeyboardType.TEXT_SHIFT, root, "kb-layout-shift.xml");
        addTypeRegion(KeyboardType.SYMBOL, root, "kb-layout-sym.xml");
        addTypeRegion(KeyboardType.SYMBOL_SHIFT, root, "kb-layout-sym-shift.xml");
        addTypeRegion(KeyboardType.CTRL, root, "kb-layout-ctrl.xml");
        addTypeRegion(KeyboardType.NUMERIC, root, "kb-layout-numeric.xml");
        addTypeRegion(KeyboardType.EMAIL, root, "kb-layout-email.xml");
        addTypeRegion(KeyboardType.URL, root, "kb-layout-url.xml");

    }

    private void addTypeRegion(KeyboardType type, String root, String file) throws IOException {
        if (handler == null) {
            logger.error("keyboard handler not loaded");
            return;
        }
        URL url = KeyboardLayoutHandler.class.getResource(root + "/" + file);
        if (url == null && Files.exists(Paths.get(root, file))) {
            url = Paths.get(root, file).toUri().toURL();
        }
        if (url != null) {
            logger.debug("add layout: {}", url);
            typeRegionMap.put(type, createKeyboardPane(handler.getLayout(url)));
            return;
        }
        String defaultRoot = getAvailableLocales().get(Locale.ENGLISH);
        if (defaultRoot == null) {
            logger.error("layout: {} / {} not found - no default available", root, file);
            return;
        }
        url = KeyboardLayoutHandler.class.getResource(defaultRoot + "/" + file);
        if (url != null) {
            logger.debug("add default layout: {}", url);
            typeRegionMap.put(type, createKeyboardPane(handler.getLayout(url)));
            return;
        }
        if (Files.exists(Paths.get(defaultRoot, file))) {
            url = Paths.get(defaultRoot, file).toUri().toURL();
            logger.debug("add default layout: {}", url);
            typeRegionMap.put(type, createKeyboardPane(handler.getLayout(url)));
            return;
        }
    }

    private Map<Locale, String> getAvailableLocales() {
        if (!availableLocales.isEmpty()) {
            return availableLocales;
        }
        if (getLayerPath() == null) {
            String layer = getLayer().toString().toLowerCase(Locale.ENGLISH);
            URL url = Objects.requireNonNull(KeyboardLayoutHandler.class.getResource("/xml/" + layer));
            logger.debug("use embedded layer path: {}", url);
            if (url.toExternalForm().contains("!")) {
                availableLocales.put(Locale.ENGLISH, "/xml/" + layer);
                readJarLocales(url);
                return availableLocales;
            }

            try {
                setLayerPath(Paths.get(url.toURI()));
            } catch (URISyntaxException e) {
                logger.error(e.getMessage(), e);
            }
        }
        availableLocales.put(Locale.ENGLISH, getLayerPath().toString());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(getLayerPath())) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) {
                    Locale l = new Locale(p.getFileName().toString());
                    availableLocales.put(l, p.toString());
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        logger.debug("locales: {}", availableLocales.keySet());
        return availableLocales;
    }

    private void readJarLocales(URL url) {
        String[] array = url.toExternalForm().split("!");
        try (FileSystem fs = FileSystems.newFileSystem(URI.create(array[0]), Collections.emptyMap())) {
            final Path path = fs.getPath(array[1]);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path p : stream) {
                    if (Files.isDirectory(p)) {
                        String lang = p.getFileName().toString().replace("/", "");
                        availableLocales.put(new Locale(lang), array[1] + "/" + lang);
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void setKeyboardType(String type) {
        KeyboardType kType;
        try {
            kType = type == null || type.isEmpty() ? KeyboardType.TEXT : KeyboardType.valueOf(type.toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {
            logger.error("unknown type: {}", type);
            kType = KeyboardType.TEXT;
        }
        setKeyboardType(kType);
    }

    public void setKeyboardType(KeyboardType type) {
        logger.debug("try to set type: {}->{}", getActiveType(), type);
        if (type.equals(getActiveType())) {
            return;
        }
        setActiveType(type);
        Region pane;
        switch (type) {
            case NUMERIC:
                setControl(false);
                setShift(false);
                setSymbol(false);
                pane = typeRegionMap.getOrDefault(type, typeRegionMap.get(KeyboardType.SYMBOL));
                break;
            case EMAIL:
                setControl(false);
                setShift(false);
                setSymbol(false);
                pane = typeRegionMap.get(type);
                break;
            case URL:
                setControl(false);
                setShift(false);
                setSymbol(false);
                pane = typeRegionMap.get(type);
                break;
            case SYMBOL:
                setControl(false);
                setShift(false);
                setSymbol(true);
                pane = typeRegionMap.get(type);
                break;
            case SYMBOL_SHIFT:
                setControl(false);
                setShift(true);
                setSymbol(true);
                pane = typeRegionMap.get(type);
                break;
            case CTRL:
                setControl(true);
                setShift(false);
                setSymbol(false);
                pane = typeRegionMap.get(type);
                break;
            case TEXT_SHIFT:
                setControl(false);
                setShift(true);
                setSymbol(false);
                pane = typeRegionMap.get(type);
                break;
            default:
                setControl(false);
                setShift(false);
                setSymbol(false);
                pane = typeRegionMap.get(type);
                break;
        }
        if (pane == null) {
            pane = typeRegionMap.get(KeyboardType.TEXT);
        }
        if (pane != null) {
            getChildren().setAll(pane);
        }
    }

    private Region createKeyboardPane(Keyboard layout) {

        GridPane rPane = new GridPane();
        rPane.setAlignment(Pos.CENTER);

        if (layout.getVerticalGap() != null) {
            rPane.setVgap(layout.getVerticalGap());
        }
        rPane.getStyleClass().add("key-background-row");

        int defaultKeyWidth = 10;
        if (layout.getKeyWidth() != null) {
            defaultKeyWidth = layout.getKeyWidth();
        }

        int defaultKeyHeight = 35;
        if (layout.getKeyHeight() != null) {
            defaultKeyHeight = layout.getKeyHeight();
        }

        int rowIdx = 0;
        for (Keyboard.Row row : layout.getRow()) {
            int colIdx = 0;
            GridPane colPane = new GridPane();
            colPane.getStyleClass().add("key-background-column");

            RowConstraints rc = new RowConstraints();
            rc.setPrefHeight(defaultKeyHeight);

            if (row.getRowEdgeFlags() != null) {
                if (row.getRowEdgeFlags().equals("bottom")) {
                    rc.setValignment(VPos.BOTTOM);
                }
                if (row.getRowEdgeFlags().equals("top")) {
                    rc.setValignment(VPos.TOP);
                }
            }
            int rowWidth = 0;
            for (Keyboard.Row.Key key : row.getKey()) {

                if (key.getHorizontalGap() != null) {
                    colPane.setHgap(key.getHorizontalGap());
                } else if (layout.getHorizontalGap() != null) {
                    colPane.setHgap(layout.getHorizontalGap());
                }
                ColumnConstraints cc = new ColumnConstraints();
                cc.setHgrow(Priority.SOMETIMES);
                cc.setFillWidth(true);
                cc.setPrefWidth(key.getKeyWidth() != null ? key.getKeyWidth() : defaultKeyWidth);

                if (key.getCodes() == null || key.getCodes().isEmpty()) {
                    // add placeholder
                    Pane placeholder = new Pane();
                    colPane.add(placeholder, colIdx, 0);
                    colPane.getColumnConstraints().add(cc);

                    logger.trace("placeholder: {}", cc);
                    colIdx++;
                    rowWidth += cc.getPrefWidth();
                    continue;
                }

                String[] codes = key.getCodes().split(",");
                KeyButton button;
                if (codes.length > 1 || key.getCodes().equals(Integer.toString(LOCALE_SWITCH))) {
                    button = new MultiKeyButton(getScale(), getStylesheets());
                } else if (Boolean.TRUE == key.isRepeatable()) {
                    button = new RepeatableKeyButton();
                } else {
                    button = new ShortPressKeyButton();
                }

                button.setFocusTraversable(false);
                button.setOnShortPressed(this);

                button.setMinHeight(10);
                button.setPrefHeight(defaultKeyHeight);
                button.setPrefWidth(defaultKeyWidth);
                button.setMaxWidth(defaultKeyWidth * 100);

                button.setMovable(Boolean.TRUE == key.isMovable());
                button.setRepeatable(Boolean.TRUE == key.isRepeatable());
                if (button.isMovable()) {
                    installMoveHandler(button);
                    button.getStyleClass().add("movable-style");
                }

                if (key.getKeyLabelStyle() != null && key.getKeyLabelStyle().startsWith(".")) {
                    for (String style : key.getKeyLabelStyle().split(";")) {
                        button.getStyleClass().add(style.substring(1));
                    }
                }
                if (Boolean.TRUE == key.isSticky()) {
                    button.getStyleClass().add("sticky-style");
                }
                if (codes.length > 0 && !codes[0].isEmpty()) {
                    button.setKeyCode(parseInt(codes[0]));
                }
                if (codes.length > 1) {
                    for (int i = 1; i < codes.length; i++) {
                        int keyCode = parseInt(codes[i]);
                        button.addExtKeyCode(keyCode, Character.toString((char) keyCode));
                    }
                }

                if (button.getKeyCode() == LOCALE_SWITCH) {
                    for (Locale l : getAvailableLocales().keySet()) {
                        button.addExtKeyCode(LOCALE_SWITCH, l.getLanguage().toUpperCase(Locale.ENGLISH));
                    }
                }

                if (key.getKeyIconStyle() != null && key.getKeyIconStyle().startsWith(".")) {
                    logger.trace("Load css style: {}", key.getKeyIconStyle());
                    Label icon = new Label();

                    for (String style : key.getKeyIconStyle().split(";")) {
                        icon.getStyleClass().add(style.substring(1));
                    }
                    icon.setMaxSize(40, 40);
                    button.setContentDisplay(ContentDisplay.CENTER);
                    button.setGraphic(icon);

                } else if (key.getKeyIconStyle() != null && key.getKeyIconStyle().startsWith("@")) {

                    try (InputStream is = KeyboardPane.class.getResourceAsStream(key.getKeyIconStyle().replace("@", "/") + ".png")) {
                        Image image = new Image(is);
                        if (!image.isError()) {
                            button.setGraphic(new ImageView(image));
                        } else {
                            logger.error("Image: {} not found", key.getKeyIconStyle());
                        }
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }

                button.setText(key.getKeyLabel() != null ? key.getKeyLabel() : Character.toString((char) button.getKeyCode()));
                button.setKeyText(key.getKeyOutputText());

                if (key.getKeyEdgeFlags() != null) {
                    if (key.getKeyEdgeFlags().equals("right")) {
                        cc.setHalignment(HPos.RIGHT);
                        button.setAlignment(Pos.BASELINE_RIGHT);
                    } else if (key.getKeyEdgeFlags().equals("left")) {
                        cc.setHalignment(HPos.LEFT);
                        button.setAlignment(Pos.BASELINE_LEFT);
                    } else {
                        cc.setHalignment(HPos.CENTER);
                    }
                } else {
                    cc.setHalignment(HPos.CENTER);
                    button.setAlignment(Pos.BASELINE_CENTER);
                }

                switch (button.getKeyCode()) {
                    case java.awt.event.KeyEvent.VK_SPACE:
                        installMoveHandler(button);
                        break;
                    case BACK_SPACE:
                    case DELETE:
                        if (!button.isRepeatable()) {
                            button.setOnLongPressed(e -> {
                                sendToComponent((char) 97, true);
                                sendToComponent((char) java.awt.event.KeyEvent.VK_DELETE, isControl());
                            });
                        }
                        break;
                    default:
                        break;
                }

                colPane.add(button, colIdx, 0);
                colPane.getColumnConstraints().add(cc);

                logger.trace("btn: {} {}", button.getText(), cc);
                colIdx++;
                rowWidth += cc.getPrefWidth();
            }
            logger.trace("row[{}] - {}", rowIdx, rowWidth);
            colPane.getRowConstraints().add(rc);
            rPane.add(colPane, 0, rowIdx);
            rowIdx++;
        }

        logger.trace("-----end pane-----");
        return rPane;
    }

    @Override
    public void handle(KeyButtonEvent event) {
        event.consume();
        KeyButtonEvent kbEvent = event;
        if (!kbEvent.getEventType().equals(KeyButtonEvent.SHORT_PRESSED)) {
            logger.warn("ignore non short pressed events");
            return;
        }
        KeyButton kb = (KeyButton) kbEvent.getSource();
        switch (kb.getKeyCode()) {
            case SHIFT_DOWN:
                // switch shifted
                setKeyboardType(isControl(), !isShift(), isSymbol());
                break;
            case SYMBOL_DOWN:
                // switch sym / qwerty
                setKeyboardType(isControl(), isShift(), !isSymbol());
                break;
            case CLOSE:
                if (closeEventHandler == null) {
                    fireEvent(new WindowEvent(this.getScene().getWindow(), WindowEvent.WINDOW_CLOSE_REQUEST));
                } else {
                    closeEventHandler.handle(event);
                }
                break;
            case TAB:
                sendToComponent((char) java.awt.event.KeyEvent.VK_TAB, true);
                break;
            case BACK_SPACE:
                sendToComponent((char) java.awt.event.KeyEvent.VK_BACK_SPACE, true);
                break;
            case DELETE:
                sendToComponent((char) java.awt.event.KeyEvent.VK_DELETE, true);
                break;
            case CTRL_DOWN:
                // switch ctrl
                setControl(!isControl());
                setKeyboardType(isControl(), isShift(), isSymbol());
                break;
            case LOCALE_SWITCH:
                switchLocale(Locale.forLanguageTag(kb.getText()));
                break;
            case ENTER:
                sendToComponent((char) java.awt.event.KeyEvent.VK_ENTER, true);
                break;
            case ARROW_UP:
                sendToComponent((char) java.awt.event.KeyEvent.VK_UP, true);
                break;
            case ARROW_DOWN:
                sendToComponent((char) java.awt.event.KeyEvent.VK_DOWN, true);
                break;
            case ARROW_LEFT:
                sendToComponent((char) java.awt.event.KeyEvent.VK_LEFT, true);
                break;
            case ARROW_RIGHT:
                sendToComponent((char) java.awt.event.KeyEvent.VK_RIGHT, true);
                break;
            case UNDO:
                sendToComponent((char) java.awt.event.KeyEvent.VK_Z, true);
                break;
            case REDO:
                sendToComponent((char) java.awt.event.KeyEvent.VK_Y, true);
                break;
            case HOME:
                sendToComponent((char) java.awt.event.KeyEvent.VK_HOME, true);
                break;
            case END:
                sendToComponent((char) java.awt.event.KeyEvent.VK_END, true);
                break;
            case PAGE_UP:
                sendToComponent((char) java.awt.event.KeyEvent.VK_PAGE_UP, true);
                break;
            case PAGE_DOWN:
                sendToComponent((char) java.awt.event.KeyEvent.VK_PAGE_DOWN, true);
                break;
            case HELP:
                sendToComponent((char) java.awt.event.KeyEvent.VK_HELP, true);
                break;
            case NUMERIC_TYPE:
                setKeyboardType(KeyboardType.NUMERIC);
                break;
            case EMAIL_TYPE:
                setKeyboardType(KeyboardType.EMAIL);
                break;
            case URL_TYPE:
                setKeyboardType(KeyboardType.URL);
                break;
            case CAPS_LOCK:
                setCapsLock(!isCapsLock());
                break;
            case PRINTSCREEN:
                double opacity = getScene().getWindow().getOpacity();
                getScene().getWindow().setOpacity(0.0);
                sendToComponent((char) java.awt.event.KeyEvent.VK_PRINTSCREEN, true);
                Timeline timeline = new Timeline();
                timeline.setDelay(Duration.millis(1000));
                timeline.getKeyFrames().add(new KeyFrame(Duration.millis(500), new KeyValue(getScene().getWindow().opacityProperty(), opacity)));
                timeline.play();
                break;
            case F1:
            case F2:
            case F3:
            case F4:
            case F5:
            case F6:
            case F7:
            case F8:
            case F9:
            case F10:
            case F11:
            case F12:
                sendToComponent((char) Math.abs(kb.getKeyCode()), true);
                break;
            default:
                if (kb.getKeyText() != null) {
                    for (int i = 0; i < kb.getKeyText().length(); i++) {
                        sendToComponent(kb.getKeyText().charAt(i), isControl());
                    }
                } else if (kb.getKeyCode() > -1) {
                    sendToComponent((char) kb.getKeyCode(), isControl());
                } else {
                    logger.debug("unknown key code: {}", kb.getKeyCode());
                    sendToComponent((char) kb.getKeyCode(), true);
                }
                if (!isCapsLock() && isShift()) {
                    setKeyboardType(isControl(), !isShift(), isSymbol());
                }
                break;
        }

    }

    /**
     * send keyEvent to iRobot implementation
     *
     * @param ch
     * @param ctrl
     */
    private void sendToComponent(char ch, boolean ctrl) {

        logger.trace("send ({}) ctrl={}", ch, ctrl);

        if (ctrl) {
            switch (Character.toUpperCase(ch)) {
                case java.awt.event.KeyEvent.VK_MINUS:
                    if (getScale() - getScaleOffset() >= getMinScale()) {
                        setScale(getScale() - getScaleOffset());
                    }
                    return;
                case 0x2B:
                    if (getScale() + getScaleOffset() <= getMaxScale()) {
                        setScale(getScale() + getScaleOffset());
                    }
                    return;

                default:
                    break;
            }
        }

        if (robots.isEmpty()) {
            logger.error("no robot handler available");
            return;
        }
        for (IRobot robot : robots) {
            robot.sendToComponent(this, ch, ctrl);
        }

    }

    public void addRobotHandler(IRobot robot) {
        robots.add(robot);
    }

    public List<IRobot> getRobotHandler() {
        return Collections.unmodifiableList(robots);
    }

    public void removeRobotHandler(IRobot robot) {
        robots.remove(robot);
    }

    public void setOnKeyboardCloseButton(EventHandler<? super Event> value) {
        closeEventHandler = value;
    }

    public final String getKeyBoardStyle() {
        return keyBoardStyle == null ? _keyBoardStyle : keyBoardStyle.get();
    }

    public final void setKeyBoardStyle(String style) {
        if (keyBoardStyle == null) {
            _keyBoardStyle = style;
        } else {
            keyBoardStyle.set(style);
        }
    }

    public final StringProperty keyBoardStyleProperty() {
        if (keyBoardStyle == null) {
            keyBoardStyle = new SimpleStringProperty(this, "keyBoardStyle", _keyBoardStyle);
        }
        return keyBoardStyle;
    }

    public final boolean isCacheLayout() {
        return cacheLayout == null ? _cacheLayout : cacheLayout.get();
    }

    public final void setCacheLayout(boolean s) {
        if (cacheLayout == null) {
            _cacheLayout = s;
        } else {
            cacheLayout.set(s);
        }
    }

    public final BooleanProperty cacheLayoutProperty() {
        if (cacheLayout == null) {
            cacheLayout = new SimpleBooleanProperty(this, "cacheLayout", _cacheLayout);
        }
        return cacheLayout;
    }

    public final boolean isSymbol() {
        return symbol == null ? _symbol : symbol.get();
    }

    public final void setSymbol(boolean s) {
        if (symbol == null) {
            _symbol = s;
        } else {
            symbol.set(s);
        }
    }

    public final BooleanProperty symbolProperty() {
        if (symbol == null) {
            symbol = new SimpleBooleanProperty(this, "symbol", _symbol);
        }
        return symbol;
    }

    public final boolean isShift() {
        return shift == null ? _shift : shift.get();
    }

    public final void setShift(boolean s) {
        if (shift == null) {
            _shift = s;
        } else {
            shift.set(s);
        }
    }

    public final BooleanProperty shiftProperty() {
        if (shift == null) {
            shift = new SimpleBooleanProperty(this, "shift", _shift);
        }
        return shift;
    }

    public final boolean isControl() {
        return control == null ? _control : control.get();
    }

    public final void setControl(boolean s) {
        if (control == null) {
            _control = s;
        } else {
            control.set(s);
        }
    }

    public final BooleanProperty controlProperty() {
        if (control == null) {
            control = new SimpleBooleanProperty(this, "control", _control);
        }
        return control;
    }

    public final boolean isSpaceKeyMove() {
        return spaceKeyMove == null ? _spaceKeyMove : spaceKeyMove.get();
    }

    public final void setSpaceKeyMove(boolean s) {
        if (spaceKeyMove == null) {
            _spaceKeyMove = s;
        } else {
            spaceKeyMove.set(s);
        }
    }

    public final BooleanProperty spaceKeyMoveProperty() {
        if (spaceKeyMove == null) {
            spaceKeyMove = new SimpleBooleanProperty(this, "spaceKeyMove", _spaceKeyMove);
        }
        return spaceKeyMove;
    }

    public final boolean isCapsLock() {
        return capsLock == null ? _capsLock : capsLock.get();
    }

    public final void setCapsLock(boolean s) {
        if (capsLock == null) {
            _capsLock = s;
        } else {
            capsLock.set(s);
        }
    }

    public final BooleanProperty capsLockProperty() {
        if (capsLock == null) {
            capsLock = new SimpleBooleanProperty(this, "capsLock", _capsLock);
        }
        return capsLock;
    }

    public final double getScaleOffset() {
        return scaleOffset == null ? _scaleOffset : scaleOffset.get();
    }

    public final void setScaleOffset(double s) {
        if (scaleOffset == null) {
            _scaleOffset = s;
        } else {
            scaleOffset.set(s);
        }
    }

    public final DoubleProperty scaleOffsetProperty() {
        if (scaleOffset == null) {
            scaleOffset = new SimpleDoubleProperty(this, "scaleOffset", _scaleOffset);
        }
        return scaleOffset;
    }

    public final double getScale() {
        return scale == null ? _scale : scale.get();
    }

    public final void setScale(double s) {
        if (scale == null) {
            _scale = s;
            setScaleX(_scale);
            setScaleY(_scale);
        } else {
            scale.set(s);
        }
    }

    public final DoubleProperty scaleProperty() {
        if (scale == null) {
            scale = new SimpleDoubleProperty(this, "scale", _scale);
            scale.addListener((l, o, s) -> {
                if (o != s) {
                    setScaleX(s.doubleValue());
                    setScaleY(s.doubleValue());
                }
            });
        }
        return scale;
    }

    public final double getMinScale() {
        return minScale == null ? _minScale : minScale.get();
    }

    public final void setMinScale(double s) {
        if (minScale == null) {
            _minScale = s;
        } else {
            minScale.set(s);
        }
    }

    public final DoubleProperty minScaleProperty() {
        if (minScale == null) {
            minScale = new SimpleDoubleProperty(this, "minScale", _minScale);
        }
        return minScale;
    }

    public final double getMaxScale() {
        return maxScale == null ? _maxScale : maxScale.get();
    }

    public final void setMaxScale(double s) {
        if (maxScale == null) {
            _maxScale = s;
        } else {
            maxScale.set(s);
        }
    }

    public final DoubleProperty maxScaleProperty() {
        if (maxScale == null) {
            maxScale = new SimpleDoubleProperty(this, "maxScale", _maxScale);
        }
        return maxScale;
    }

    public final DefaultLayer getLayer() {
        return layer == null ? _layer : layer.get();
    }

    public final void setLayer(DefaultLayer l) {
        if (layer == null) {
            _layer = l;
        } else {
            layer.set(l);
        }
    }

    public final ObjectProperty<DefaultLayer> layerProperty() {
        if (layer == null) {
            layer = new SimpleObjectProperty<>(this, "layer", _layer);
        }
        return layer;
    }

    public final Path getLayerPath() {
        return layerPath == null ? _layerPath : layerPath.get();
    }

    public final void setLayerPath(Path l) {
        if (layerPath == null) {
            _layerPath = l;
        } else {
            layerPath.set(l);
        }
    }

    public final ObjectProperty<Path> layerPathProperty() {
        if (layerPath == null) {
            layerPath = new SimpleObjectProperty<>(this, "layerPath", _layerPath);
        }
        return layerPath;
    }

    public final Locale getLocale() {
        return locale == null ? _locale : locale.get();
    }

    public final void setLocale(Locale l) {
        if (locale == null) {
            _locale = l;
        } else {
            locale.set(l);
        }
    }

    public final ObjectProperty<Locale> localeProperty() {
        if (locale == null) {
            locale = new SimpleObjectProperty<>(this, "locale", _locale);
        }
        return locale;
    }

    public final Locale getActiveLocale() {
        return activeLocale == null ? _activeLocale : activeLocale.get();
    }

    public final void setActiveLocale(Locale l) {
        if (activeLocale == null) {
            _activeLocale = l;
        } else {
            activeLocale.set(l);
        }
    }

    public final ObjectProperty<Locale> activeLocaleProperty() {
        if (activeLocale == null) {
            activeLocale = new SimpleObjectProperty<>(this, "activeLocale", _activeLocale);
        }
        return activeLocale;
    }

    public final KeyboardType getActiveType() {
        return activeType == null ? _activeType : activeType.get();
    }

    public final void setActiveType(KeyboardType l) {
        if (activeType == null) {
            _activeType = l;
        } else {
            activeType.set(l);
        }
    }

    public final ObjectProperty<KeyboardType> activeTypeProperty() {
        if (activeType == null) {
            activeType = new SimpleObjectProperty<>(this, "activeType", _activeType);
        }
        return activeType;
    }

    private void installMoveHandler(Node node) {
        if (movedHandler == null) {
            movedHandler = (e) -> {
                if (isSpaceKeyMove()) {
                    mousePressedX = getScene().getWindow().getX() - e.getScreenX();
                    mousePressedY = getScene().getWindow().getY() - e.getScreenY();
                }
            };
        }
        if (draggedHandler == null) {
            draggedHandler = (e) -> {
                if (isSpaceKeyMove()) {
                    getScene().getWindow().setX(e.getScreenX() + mousePressedX);
                    getScene().getWindow().setY(e.getScreenY() + mousePressedY);
                }
            };
        }
        node.setOnMouseMoved(movedHandler);
        node.setOnMouseDragged(draggedHandler);
    }

}
