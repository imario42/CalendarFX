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

import com.calendarfx.model.Calendar;
import com.calendarfx.model.CalendarEvent;
import com.calendarfx.model.CalendarSource;
import com.calendarfx.model.Entry;
import com.calendarfx.util.LoggingDomain;
import com.calendarfx.util.Util;
import com.calendarfx.view.AllDayEntryView;
import com.calendarfx.view.AllDayView;
import com.calendarfx.view.DraggedEntry;
import com.calendarfx.view.EntryViewBase;
import impl.com.calendarfx.view.util.Placement;
import impl.com.calendarfx.view.util.Resolver;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AllDayViewSkin extends DateControlSkin<AllDayView> implements LoadDataSettingsProvider {

    private static final String ALL_DAY_BACKGROUND_REGION = "day-region"; //$NON-NLS-1$
    private static final String ALL_DAY_BACKGROUND_REGION_TODAY = "today"; //$NON-NLS-1$
    private static final String ALL_DAY_BACKGROUND_REGION_WEEKEND = "weekend"; //$NON-NLS-1$

    private static final String PROPERTY_INDICATOR = "calendarfx_indicator";

    private final AllDayView view;

    private DataLoader dataLoader;

    private StackPane stackPane;
    private Pane scrollPane;
    private Pane elementsPane;
    private List<Region> indicators = new ArrayList<>();
    private GridPane gridPane;
    private ScrollBar allDayScrollBar;

    public AllDayViewSkin(AllDayView view) {
        super(view);
        this.view = view;

        view.setFocusTraversable(true);

        gridPane = new GridPane();
        gridPane.getStyleClass().add("container");

        stackPane = new StackPane();
        stackPane.getStyleClass().add("all-day-view");

        elementsPane = new Pane();
        elementsPane.getStyleClass().add("all-day-view");
        elementsPane.setMinSize(Control.USE_PREF_SIZE, Control.USE_PREF_SIZE);
        elementsPane.setPrefSize(Control.USE_COMPUTED_SIZE, Control.USE_COMPUTED_SIZE);
        elementsPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        scrollPane = new Pane();
        scrollPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        scrollPane.getChildren().add(elementsPane);

        StackPane.setAlignment(scrollPane, Pos.TOP_LEFT);

        stackPane.getChildren().addAll(gridPane, scrollPane);

        getChildren().add(stackPane);

        // update backgrounds
        InvalidationListener updateBackgroundsListener = evt -> updateBackgrounds();
        view.numberOfDaysProperty().addListener(updateBackgroundsListener);
        view.showTodayProperty().addListener(updateBackgroundsListener);
        view.weekFieldsProperty().addListener(updateBackgroundsListener);
        view.adjustToFirstDayOfWeekProperty().addListener(updateBackgroundsListener);

        // update entries
        InvalidationListener updateEntriesListener = evt -> updateEntries("a view property changed");
        view.numberOfDaysProperty().addListener(updateEntriesListener);
        view.dateProperty().addListener(updateEntriesListener);
        view.extraPaddingProperty().addListener(updateEntriesListener);
        view.rowHeightProperty().addListener(updateEntriesListener);
        view.rowSpacingProperty().addListener(updateEntriesListener);
        view.columnSpacingProperty().addListener(updateEntriesListener);
        view.weekFieldsProperty().addListener(updateEntriesListener);
        view.adjustToFirstDayOfWeekProperty().addListener(updateEntriesListener);

        updateBackgrounds();

        dataLoader = new DataLoader(this);

        updateEntries("initial load");
    }

    @Override
    protected void refreshData() {
        updateEntries("refreshData() called");
    }

    private void updateEntries(String reason) {
        LoggingDomain.PERFORMANCE.fine("updating entries, reason: " + reason);

        elementsPane.getChildren().removeIf(child -> child instanceof AllDayEntryView);

        Map<LocalDate, List<Entry<?>>> dataMap = new HashMap<>();
        dataLoader.loadEntries(dataMap);

        Set<Entry<?>> entrySet = new HashSet<>();
        for (List<Entry<?>> entryList : dataMap.values()) {
            entrySet.addAll(entryList);
        }

        entrySet.removeIf(ref -> !ref.isFullDay());

        List<Entry<?>> entryList = new ArrayList<>(entrySet);

        for (Entry<?> entry : entryList) {
            doAddEntryView(entry);
        }

        getSkinnable().autosize();
    }

    private boolean removeEntryView(Entry<?> entry) {
        boolean removed = elementsPane.getChildren().removeIf(node -> {
            if (node instanceof AllDayEntryView) {
                AllDayEntryView view = (AllDayEntryView) node;

                Entry<?> removedEntry = entry;
                if (removedEntry.getRecurrenceSourceEntry() != null) {
                    removedEntry = removedEntry.getRecurrenceSourceEntry();
                }

                Entry<?> viewEntry = view.getEntry();
                if (viewEntry.getRecurrenceSourceEntry() != null) {
                    viewEntry = viewEntry.getRecurrenceSourceEntry();
                }

                if (viewEntry.getId().equals(removedEntry.getId())) {
                    return true;
                }
            }

            return false;
        });

        if (removed && !(entry instanceof DraggedEntry) && LoggingDomain.VIEW.isLoggable(Level.FINE)) {
            LoggingDomain.VIEW.fine("successfully removed the entry view of entry " + entry);
        }

        return removed;
    }

    private void addEntryView(Entry<?> entry) {
        if (entry.isRecurring()) {
            Calendar calendar = entry.getCalendar();
            final Map<LocalDate, List<Entry<?>>> entries = calendar.findEntries(getLoadStartDate(), getLoadEndDate(), getZoneId());
            for (LocalDate date : entries.keySet()) {
                List<Entry<?>> entriesOnDate = entries.get(date);
                if (entriesOnDate != null) {
                    entriesOnDate.forEach(this::doAddEntryView);
                }
            }
        } else {
            doAddEntryView(entry);
        }
    }

    private AllDayEntryView doAddEntryView(Entry<?> entry) {
        Callback<Entry<?>, AllDayEntryView> factory = getSkinnable().getEntryViewFactory();
        AllDayEntryView view = factory.call(entry);
        view.applyCss(); // TODO: really needed
        view.getProperties().put("control", getSkinnable()); //$NON-NLS-1$
        view.setManaged(false);

        int index = findIndex(entry);

        elementsPane.getChildren().add(index, view);

        if (!(entry instanceof DraggedEntry) && LoggingDomain.VIEW.isLoggable(Level.FINE)) {
            LoggingDomain.VIEW.fine("added entry view " + entry.getTitle() + ", day = " + getSkinnable().getDate());
        }

        return view;
    }

    /*
     * Utility method to find the right place for inserting a new day entry
     * view. The right order is important for TAB traversal to work properly.
     */
    private int findIndex(Entry<?> entry) {
        int childrenSize = elementsPane.getChildren().size();

        for (int i = 0; i < childrenSize; i++) {
            Node node = elementsPane.getChildren().get(i);
            if (node instanceof AllDayEntryView) {
                AllDayEntryView view = (AllDayEntryView) node;
                Entry<?> viewEntry = view.getEntry();
                if (viewEntry.getStartAsZonedDateTime().isAfter(entry.getStartAsZonedDateTime())) {
                    return i;
                }
            }
        }

        return childrenSize;
    }

    @Override
    protected void calendarChanged(Calendar calendar) {
        updateEntries("calendar changed");
    }

    @Override
    protected void entryCalendarChanged(CalendarEvent evt) {
        Entry<?> entry = evt.getEntry();

        /*
         * We only care about full day entries in this view.
         */
        if (!entry.isFullDay()) {
            return;
        }

        if (evt.isEntryRemoved()) {
            removeEntryView(entry);
            getSkinnable().requestLayout();
        }

        if (evt.isEntryAdded() && isRelevant(entry)) {
            addEntryView(entry);
            getSkinnable().requestLayout();
        }
    }

    @Override
    protected void entryFullDayChanged(CalendarEvent evt) {
        Entry<?> entry = evt.getEntry();
        if (isRelevant(entry)) {
            if (entry.isFullDay()) {
                addEntryView(entry);
            } else {
                removeEntryView(entry);
            }
            getSkinnable().requestLayout();
        }
    }

    @Override
    protected void entryRecurrenceRuleChanged(CalendarEvent evt) {
        Entry<?> entry = evt.getEntry();

        /*
         * We only care about full day entries in this view.
         */
        if (!entry.isFullDay()) {
            return;
        }

        removeEntryView(entry);
        addEntryView(entry);
    }

    @Override
    protected void entryIntervalChanged(CalendarEvent evt) {
        Entry<?> entry = evt.getEntry();

        /*
         * We only care about full day entries in this view.
         */
        if (!entry.isFullDay()) {
            return;
        }

        removeEntryView(entry);

        if (isRelevant(entry)) {
            if (entry.isRecurring()) {
                Calendar calendar = entry.getCalendar();
                final Map<LocalDate, List<Entry<?>>> entriesMap = calendar.findEntries(getLoadStartDate(), getLoadEndDate(), getZoneId());
                List<Entry<?>> entries = entriesMap.get(getSkinnable().getDate());
                if (entries != null) {
                    for (Entry<?> e : entries) {
                        if (e.getId().equals(entry.getId())) {
                            addEntryView(e);

                            /*
                             * We only support recurrence for temporal units larger than days, so there can only
                             * be one entry.
                             */
                            break;
                        }
                    }
                }
            } else {
                addEntryView(entry);
            }
        }
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {

        List<AllDayEntryView> entryViews = elementsPane.getChildren().stream().filter(node -> node instanceof AllDayEntryView).map(node -> (AllDayEntryView) node).collect(Collectors.toList());

        List<Placement> placements = Resolver.resolve(entryViews);

        int maxPosition = 0;
        for (Placement p : placements) {
            maxPosition = Math.max(maxPosition, p.getColumnIndex());
        }

        Insets insets = getSkinnable().getInsets();
        Insets extraPadding = getSkinnable().getExtraPadding();

        double rowHeight = getSkinnable().getRowHeight();
        double rowSpacing = getSkinnable().getRowSpacing();
        return (maxPosition + 1) * rowHeight + (maxPosition * rowSpacing) + insets.getTop() + insets.getBottom() * extraPadding.getTop() + extraPadding.getBottom();
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        super.layoutChildren(contentX, contentY, contentWidth, contentHeight);

        double rowHeight = getSkinnable().getRowHeight();
        double rowSpacing = getSkinnable().getRowSpacing();

        double height = 0;

        Insets extraPadding = getSkinnable().getExtraPadding();

        List<AllDayEntryView> entryViews = elementsPane.getChildren().stream().filter(node -> node instanceof AllDayEntryView).map(node -> (AllDayEntryView) node).collect(Collectors.toList());

        List<Placement> placements = Resolver.resolve(entryViews);

        for (Placement placement : placements) {
            EntryViewBase<?> view = placement.getEntryView();
            Entry<?> entry = view.getEntry();

            LocalDate startDate = getSkinnable().getDate();
            if (getSkinnable().isAdjustToFirstDayOfWeek()) {
                startDate = Util.adjustToFirstDayOfWeek(getSkinnable().getDate(), getSkinnable().getFirstDayOfWeek());
            }

            LocalDate endDate = startDate.plusDays(getSkinnable().getNumberOfDays() - 1);

            long deltaDays = ChronoUnit.DAYS.between(startDate, entry.getStartDate());

            long entryDurationInDays = ChronoUnit.DAYS.between(entry.getStartDate(), entry.getEndDate()) + 1;

            if (deltaDays < 0) {
                entryDurationInDays += deltaDays;
            }

            if (entry.getStartDate().isBefore(startDate)) {
                view.getProperties().put("startDate", startDate); //$NON-NLS-1$
            } else {
                view.getProperties().put("startDate", entry.getStartDate()); //$NON-NLS-1$
            }

            if (entry.getEndDate().isAfter(endDate)) {
                view.getProperties().put("endDate", endDate); //$NON-NLS-1$
            } else {
                view.getProperties().put("endDate", entry.getEndDate()); //$NON-NLS-1$
            }

            entryDurationInDays = Math.max(entryDurationInDays, 1);

            double dayWidth = contentWidth / getSkinnable().getNumberOfDays();

            double x = Math.max(0, contentX + (deltaDays * dayWidth));
            double y = contentY + placement.getColumnIndex() * (rowHeight + rowSpacing) + extraPadding.getTop();

            double w;
            if (getSkinnable().getNumberOfDays() == 1) {
                w = contentWidth + 1;
            } else {
                w = Math.min(entryDurationInDays * dayWidth - getSkinnable().getColumnSpacing(), contentWidth - x);
            }

            view.setMaxHeight(rowHeight);

            view.resizeRelocate(snapPosition(x), snapPosition(y), snapSize(w), snapSize(rowHeight));

            height = Math.max(height, y + rowHeight);
        }

        elementsPane.setMaxHeight(height);
        double prefHeight = computePrefHeight(0,0, 0, 0, 0);
        System.err.println("resize: " + prefHeight);
        elementsPane.resize(view.getWidth(), prefHeight);
        elementsPane.setPrefHeight(prefHeight);
    }

    private void updateBackgrounds() {
        // the day views

        stackPane.getChildren().removeAll(indicators);
        indicators.clear();

        gridPane.getChildren().clear();
        List<ColumnConstraints> constraints = new ArrayList<>();

        int numberOfDays = getSkinnable().getNumberOfDays();
        for (int i = 0; i < numberOfDays; i++) {
            ColumnConstraints con = new ColumnConstraints();
            con.setPercentWidth((double) 100 / (double) numberOfDays);
            constraints.add(con);
            Region region = new Region();
            region.setMaxWidth(Double.MAX_VALUE);
            region.getStyleClass().add(ALL_DAY_BACKGROUND_REGION);
            GridPane.setHgrow(region, Priority.ALWAYS);
            GridPane.setVgrow(region, Priority.ALWAYS);
            GridPane.setFillHeight(region, true);
            GridPane.setFillWidth(region, true);

            final int day = i;
            getSkinnable().dateProperty().addListener(evt -> updateRegion(region, day));
            updateRegion(region, day);

            gridPane.add(region, i, 0);

            Region indicator = new Region();
            indicator.getStyleClass().add(ALL_DAY_BACKGROUND_REGION);
            indicator.setManaged(true);
            indicator.setMouseTransparent(true);
            indicator.setFocusTraversable(false);
            region.boundsInParentProperty().addListener((observable, oldValue, newValue) -> {
                indicator.resizeRelocate(
                        newValue.getMinX(),
                        newValue.getMinY(),
                        newValue.getWidth(),
                        ((Region) gridPane.getParent()).getHeight());
            });
            indicators.add(indicator);

            region.getProperties().put(PROPERTY_INDICATOR, indicator);
        }

        gridPane.getColumnConstraints().setAll(constraints);
        stackPane.getChildren().addAll(indicators);

        getSkinnable().requestLayout();
    }

    private void updateRegion(Region region, int day) {
        final AllDayView view = getSkinnable();
        final Region indicator = (Region) region.getProperties().get(PROPERTY_INDICATOR);

        LocalDate startDate = view.getDate();

        if (view.isAdjustToFirstDayOfWeek()) {
            startDate = Util.adjustToFirstDayOfWeek(view.getDate(), view.getFirstDayOfWeek());
        }

        LocalDate date = getDate(startDate, day);

        if (view.isShowToday() && date.equals(view.getToday())) {
            if (!region.getStyleClass().contains(ALL_DAY_BACKGROUND_REGION_TODAY)) {
                region.getStyleClass().add(ALL_DAY_BACKGROUND_REGION_TODAY);
                if (indicator != null) {
                    indicator.getStyleClass().add(ALL_DAY_BACKGROUND_REGION_TODAY);
                }
            }
        } else {
            region.getStyleClass().remove(ALL_DAY_BACKGROUND_REGION_TODAY);
            if (indicator != null) {
                indicator.getStyleClass().remove(ALL_DAY_BACKGROUND_REGION_TODAY);
            }
        }

        if (view.getWeekendDays().contains(date.getDayOfWeek())) {
            if (!region.getStyleClass().contains(ALL_DAY_BACKGROUND_REGION_WEEKEND)) {
                region.getStyleClass().add(ALL_DAY_BACKGROUND_REGION_WEEKEND);
                if (indicator != null) {
                    indicator.getStyleClass().add(ALL_DAY_BACKGROUND_REGION_WEEKEND);
                }
            }
        } else {
            region.getStyleClass().remove(ALL_DAY_BACKGROUND_REGION_WEEKEND);
            if (indicator != null) {
                indicator.getStyleClass().remove(ALL_DAY_BACKGROUND_REGION_WEEKEND);
            }
        }
    }

    private LocalDate getDate(LocalDate startDate, int dayCount) {
        return startDate.plusDays(dayCount);
    }

    @Override
    public String getLoaderName() {
        return "All Day View"; //$NON-NLS-1$
    }

    @Override
    public LocalDate getLoadStartDate() {
        AllDayView view = getSkinnable();

        if (view.isAdjustToFirstDayOfWeek()) {

            /*
             * The month view also shows the last couple of days of the previous
             * month.
             */
            return Util.adjustToFirstDayOfWeek(view.getDate(), view.getFirstDayOfWeek());

        }

        return view.getDate();
    }

    @Override
    public LocalDate getLoadEndDate() {
        return getLoadStartDate().plusDays(getSkinnable().getNumberOfDays() - 1);
    }

    @Override
    public ZoneId getZoneId() {
        return ZoneId.systemDefault();
    }

    @Override
    public List<CalendarSource> getCalendarSources() {
        return getSkinnable().getCalendarSources();
    }

    @Override
    public Control getControl() {
        return getSkinnable();
    }

    @Override
    public boolean isCalendarVisible(Calendar calendar) {
        return getSkinnable().isCalendarVisible(calendar);
    }

    protected void setupScrollBar(ScrollBar allDayScrollBar, DoubleProperty maxHeight) {
        this.allDayScrollBar = allDayScrollBar;

        this.allDayScrollBar.setOrientation(Orientation.VERTICAL);

        final double borderHeight = 5.0;

        final InvalidationListener scroller = (observable) -> elementsPane.setTranslateY(allDayScrollBar.getValue() * -1);

        if (this.allDayScrollBar.getParent() != null) {
            bindScrollBar(allDayScrollBar, maxHeight, borderHeight, scroller);
        }
        this.allDayScrollBar.parentProperty().addListener((observable, oldParent, newParent) -> {
            if (newParent != null) {
                bindScrollBar(allDayScrollBar, maxHeight, borderHeight, scroller);
            } else {
                unbindScrollBar(allDayScrollBar, scroller);
            }
            this.allDayScrollBar.requestLayout();
        });
    }

    private void unbindScrollBar(ScrollBar allDayScrollBar, InvalidationListener scroller) {
        allDayScrollBar.prefHeightProperty().unbind();
        allDayScrollBar.maxProperty().unbind();
        allDayScrollBar.visibleAmountProperty().unbind();
        allDayScrollBar.valueProperty().removeListener(scroller);
        this.allDayScrollBar.setValue(0.0);
        view.maxHeightProperty().unbind();
        view.setMaxHeight(Double.MAX_VALUE);
        scrollPane.setClip(null);
        view.setClip(null);
    }

    private void bindScrollBar(ScrollBar allDayScrollBar, DoubleProperty maxHeight, double borderHeight, InvalidationListener scroller) {
        view.maxHeightProperty().bind(maxHeight);

        allDayScrollBar.prefHeightProperty().bind(Bindings
                .when(Bindings.greaterThan(elementsPane.heightProperty(), view.heightProperty())).then(view.heightProperty()).otherwise(elementsPane.heightProperty()));

        allDayScrollBar.maxProperty().bind(elementsPane.heightProperty().subtract(allDayScrollBar.heightProperty()));

        allDayScrollBar.valueProperty().addListener(scroller);

        Rectangle clipView = new Rectangle();
        clipView.widthProperty().bind(stackPane.widthProperty());
        clipView.heightProperty().bind(view.maxHeightProperty());
        view.setClip(clipView);

        Rectangle clipElements = new Rectangle();
        clipElements.widthProperty().bind(stackPane.widthProperty());
        clipElements.heightProperty().bind(view.maxHeightProperty().subtract(borderHeight));
        scrollPane.setClip(clipElements);
    }
}
