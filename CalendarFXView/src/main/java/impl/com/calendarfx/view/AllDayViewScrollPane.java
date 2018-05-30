/*
 *  Copyright (C) 2017 Dirk Lemmermann Software & Consulting (dlsc.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package impl.com.calendarfx.view;

import com.calendarfx.view.AllDayView;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

import java.util.Objects;

/**
 * A specialized scrollpane used for automatic scrolling when the user performs
 * a drag operation close to the edges of the pane.
 */
public class AllDayViewScrollPane extends Pane {

    private final AllDayView allDayView;

    private final ScrollBar scrollBar;

    private SimpleDoubleProperty allDayScrollHeightProperty = new SimpleDoubleProperty(0.0);

    /**
     * Constructs a new scrollpane for the given content node.
     *
     * @param allDayView the content node
     */
    public AllDayViewScrollPane(AllDayView allDayView, ScrollBar scrollBar) {
        super();

        getStyleClass().add("all-day-view-scroll");

        setMinHeight(Control.USE_PREF_SIZE);
        setMaxHeight(Control.USE_PREF_SIZE);

        this.scrollBar = scrollBar;

        this.allDayView = Objects.requireNonNull(allDayView);
        this.allDayView.setManaged(false);
        this.allDayView.layoutBoundsProperty().addListener(it -> requestLayout());

        scrollBar.setOrientation(Orientation.VERTICAL);
        scrollBar.maxProperty().bind(allDayView.heightProperty().subtract(heightProperty()));
        scrollBar.visibleAmountProperty()
                .bind(Bindings.multiply(scrollBar.maxProperty(),
                        Bindings.divide(heightProperty(), allDayView.heightProperty())));
        scrollBar.valueProperty().addListener(it -> allDayView.setTranslateY(scrollBar.getValue() * -1));

        scrollBar.parentProperty().addListener(evt -> {
            this.allDayView.setTranslateY(0);
            requestLayout();
        });
        allDayScrollHeightProperty.addListener(evt -> requestLayout());

        // user clicks on scrollbar arrows -> scroll one hour
        scrollBar.unitIncrementProperty().bind(allDayView.rowHeightProperty());

        // user clicks in backround of scrollbar = block scroll -> scroll half a page
        scrollBar.blockIncrementProperty().bind(heightProperty().divide(2));

        allDayView.translateYProperty().addListener(it -> {
            scrollBar.setValue(-allDayView.getTranslateY());
        });

        getChildren().add(allDayView);

        addEventFilter(ScrollEvent.SCROLL, evt -> scrollY(evt.getDeltaY()));

        // regular drag, e.g. of an entry view
        addEventFilter(MouseEvent.MOUSE_DRAGGED, this::autoscrollIfNeeded);
        addEventFilter(MouseEvent.MOUSE_RELEASED, evt -> stopAutoScrollIfNeeded());

        // drag and drop from the outside
        // TODO: PUT BACK IN addEventFilter(MouseEvent.DRAG_DETECTED, evt -> startDrag(evt));
        addEventFilter(DragEvent.DRAG_OVER, this::autoscrollIfNeeded);
        addEventFilter(DragEvent.DRAG_EXITED, evt -> stopAutoScrollIfNeeded());
        addEventFilter(DragEvent.DRAG_DROPPED, evt -> stopAutoScrollIfNeeded());
        addEventFilter(DragEvent.DRAG_DONE, evt -> stopAutoScrollIfNeeded());

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
    }

    private void scrollY(double deltaY) {
        Insets insets = getInsets();
        allDayView.setTranslateY(Math.min(0, Math.max(allDayView.getTranslateY() + deltaY, getMaxTranslateY(insets))));
    }

    public SimpleDoubleProperty allDayScrollHeightProperty() {
        return allDayScrollHeightProperty;
    }

    @Override
    protected double computePrefWidth(double height) {
        return allDayView.prefWidth(-1);
    }

    @Override
    protected double computePrefHeight(double width) {
        return getViewportHeight();
    }

    public double getViewportHeight() {
        double listHeight = allDayView.prefHeight(-1);

        if (scrollBar.getParent() == null) {
            return listHeight;
        }
        else {
            return listHeight <= allDayScrollHeightProperty.get() ? listHeight:allDayScrollHeightProperty.get();
        }
    }

    @Override
    protected void layoutChildren() {

        final double ph = allDayView.prefHeight(-1);

        Insets insets = getInsets();
        allDayView.resizeRelocate(
                snapPosition(insets.getLeft()),
                snapPosition(insets.getTop()),
                snapSize(getWidth() - insets.getLeft() - insets.getRight()),
                snapSize(Math.max(ph, getHeight() - insets.getTop() - insets.getBottom())));
    }

    private double getMaxTranslateY(Insets insets) {
        return (getHeight() - insets.getTop() - insets.getBottom()) - allDayView.getHeight();
    }

    private void autoscrollIfNeeded(DragEvent evt) {
        evt.acceptTransferModes(TransferMode.ANY);

        if (getBoundsInLocal().getWidth() < 1) {
            if (getBoundsInLocal().getWidth() < 1) {
                stopAutoScrollIfNeeded();
                return;
            }
        }

        double yOffset = 0;

        // y offset

        double delta = evt.getSceneY() - localToScene(0, 0).getY();
        double proximity = 20;
        if (delta < proximity) {
            yOffset = -(proximity - delta);
        }

        delta = localToScene(0, 0).getY() + getHeight() - evt.getSceneY();
        if (delta < proximity) {
            yOffset = proximity - delta;
        }

        if (yOffset != 0) {
            autoscroll(yOffset);
        } else {
            stopAutoScrollIfNeeded();
        }
    }

    private void autoscrollIfNeeded(MouseEvent evt) {
        if (getBoundsInLocal().getWidth() < 1) {
            if (getBoundsInLocal().getWidth() < 1) {
                stopAutoScrollIfNeeded();
                return;
            }
        }

        double yOffset = 0;

        // y offset

        double delta = evt.getSceneY() - localToScene(0, 0).getY();
        if (delta < 0) {
            yOffset = Math.max(delta / 2, -10);
        }

        delta = localToScene(0, 0).getY() + getHeight() - evt.getSceneY();
        if (delta < 0) {
            yOffset = Math.min(-delta / 2, 10);
        }

        if (yOffset != 0) {
            autoscroll(yOffset);
        } else {
            stopAutoScrollIfNeeded();
        }
    }

    class ScrollThread extends Thread {
        private boolean running = true;
        private double yOffset;

        public ScrollThread() {
            super("Autoscrolling List View"); //$NON-NLS-1$
            setDaemon(true);
        }

        @Override
        public void run() {

            /*
             * Some initial delay, especially useful when dragging something in
             * from the outside.
             */

            try {
                Thread.sleep(300);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

            while (running) {

                Platform.runLater(this::scrollToY);

                try {
                    sleep(15);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private void scrollToY() {
            scrollY(-yOffset);
        }

        public void stopRunning() {
            this.running = false;
        }

        public void setDelta(double yOffset) {
            this.yOffset = yOffset;
        }
    }

    private ScrollThread scrollThread;

    private void autoscroll(double yOffset) {
        if (scrollThread == null) {
            scrollThread = new ScrollThread();
            scrollThread.start();
        }

        scrollThread.setDelta(yOffset);
    }

    private void stopAutoScrollIfNeeded() {
        if (scrollThread != null) {
            scrollThread.stopRunning();
            scrollThread = null;
        }
    }
}