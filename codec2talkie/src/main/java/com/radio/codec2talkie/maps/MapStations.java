package com.radio.codec2talkie.maps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.radio.codec2talkie.R;
import com.radio.codec2talkie.protocol.aprs.tools.AprsSymbolTable;
import com.radio.codec2talkie.protocol.position.Position;
import com.radio.codec2talkie.storage.position.PositionItemViewModel;
import com.radio.codec2talkie.storage.station.StationItem;
import com.radio.codec2talkie.storage.station.StationItemViewModel;
import com.radio.codec2talkie.tools.DateTools;
import com.radio.codec2talkie.tools.UnitTools;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class MapStations {
    private static final String TAG = MapTrack.class.getSimpleName();

    private final Context _context;

    private final AprsSymbolTable _aprsSymbolTable;

    private final PositionItemViewModel _positionItemViewModel;
    private final ViewModelStoreOwner _owner;
    private final MapView _mapView;

    private final MarkerInfoWindow _infoWindow;

    private final HashMap<String, Marker> _objectOverlayItems = new HashMap<>();
    private final HashMap<String, Polygon> _objectOverlayRangeCircles = new HashMap<>();

    private boolean _showCircles = false;

    private final MapTrack _activeTrack;

    public MapStations(Context context, MapView mapView, ViewModelStoreOwner owner) {
        _context = context;
        _owner = owner;
        _mapView = mapView;
        _positionItemViewModel = new ViewModelProvider(_owner).get(PositionItemViewModel.class);

        _aprsSymbolTable = AprsSymbolTable.getInstance(context);
        _infoWindow = new MarkerInfoWindow(R.layout.bonuspack_bubble, _mapView);
        _activeTrack = new MapTrack(_mapView, _owner);

        StationItemViewModel _stationItemViewModel = new ViewModelProvider(_owner).get(StationItemViewModel.class);
        // FIXME, room livedata sends all list if one item changed event with distinctUntilChanged
        _stationItemViewModel.getAllStationItems().observe((LifecycleOwner) _owner, allStations -> {
            Log.i(TAG, "add stations " + allStations.size());
            for (StationItem station : allStations) {
                //Log.i(TAG, "new position " + station.getLatitude() + " " + station.getLongitude());
                // do not add items without coordinate
                if (station.getMaidenHead() == null) continue;
                if (addStationPositionIcon(station)) {
                    addRangeCircle(station);
                }
            }
        });
    }

    public void showRangeCircles(boolean isVisible) {
        _showCircles = true;
        for (Polygon polygon : _objectOverlayRangeCircles.values()) {
            polygon.setVisible(isVisible);
        }
    }

    private void addRangeCircle(StationItem group) {
        if (group.getRangeMiles() == 0) return;
        String callsign = group.getSrcCallsign();
        Polygon polygon = null;

        if (_objectOverlayRangeCircles.containsKey(callsign)) {
            polygon = _objectOverlayRangeCircles.get(callsign);
            assert polygon != null;
        }

        if (polygon == null) {
            polygon = new Polygon();
            polygon.setVisible(_showCircles);

            Paint p = polygon.getOutlinePaint();
            p.setStrokeWidth(1);

            _mapView.getOverlayManager().add(0, polygon);
            _objectOverlayRangeCircles.put(callsign, polygon);
        }
        ArrayList<GeoPoint> circlePoints = new ArrayList<>();
        for (float f = 0; f < 360; f += 6) {
            circlePoints.add(new GeoPoint(group.getLatitude(), group.getLongitude()).destinationPoint(1000 * UnitTools.milesToKilometers(group.getRangeMiles()), f));
        }
        polygon.setPoints(circlePoints);
    }

    private boolean addStationPositionIcon(StationItem group) {
        String callsign = group.getSrcCallsign();
        Marker marker = null;

        String newTitle = DateTools.epochToIso8601(group.getTimestampEpoch()) + " " + callsign;
        String newSnippet = getStatus(group);

        // find old marker
        if (_objectOverlayItems.containsKey(callsign)) {
            marker = _objectOverlayItems.get(callsign);
            assert marker != null;

            // skip if unchanged
            GeoPoint oldPosition = marker.getPosition();
            if (oldPosition.getLatitude() == group.getLatitude() &&
                    oldPosition.getLongitude() == group.getLongitude() &&
                    marker.getTitle().equals(newTitle) &&
                    marker.getSnippet().equals(newSnippet)) {

                return false;
            }
        }

        // create new marker
        if (marker == null) {
            // icon from symbol
            Bitmap bitmapIcon = _aprsSymbolTable.bitmapFromSymbol(group.getSymbolCode(), false);
            if (bitmapIcon == null) return false;
            Bitmap bitmapInfoIcon = _aprsSymbolTable.bitmapFromSymbol(group.getSymbolCode(), true);
            if (bitmapInfoIcon == null) return false;

            // construct and calculate bounds
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.FILL);
            Rect bounds = new Rect();
            paint.getTextBounds(callsign, 0, callsign.length(), bounds);
            int width = Math.max(bitmapIcon.getWidth(), bounds.width());
            int height = bitmapIcon.getHeight() + bounds.height();

            // create overlay bitmap
            Bitmap bitmap = Bitmap.createBitmap(width, height, null);
            bitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);

            // draw APRS icon
            Canvas canvas = new Canvas(bitmap);
            float bitmapLeft = width > bitmapIcon.getWidth() ? width / 2.0f - bitmapIcon.getWidth() / 2.0f : 0;
            // do not rotate
            if (group.getBearingDegrees() == 0 || !AprsSymbolTable.needsRotation(group.getSymbolCode())) {
                canvas.drawBitmap(bitmapIcon, bitmapLeft, 0, null);
                // rotate
            } else {
                float rotationDeg = (float) (group.getBearingDegrees() - 90.0f);
                Matrix m = new Matrix();
                // flip/rotate
                if (group.getBearingDegrees() > 180) {
                    m.postScale(-1, 1);
                    m.postTranslate(bitmapIcon.getWidth(), 0);
                    m.postRotate(rotationDeg - 180, bitmapIcon.getWidth() / 2.0f, bitmapIcon.getHeight() / 2.0f);
                    // rotate
                } else {
                    m.postRotate(rotationDeg, bitmapIcon.getWidth() / 2.0f, bitmapIcon.getHeight() / 2.0f);
                }
                m.postTranslate(bitmapLeft, 0);
                canvas.drawBitmap(bitmapIcon, m, null);
            }

            // draw background
            paint.setColor(Color.WHITE);
            paint.setAlpha(120);
            bounds.set(0, bitmapIcon.getHeight(), width, height);
            canvas.drawRect(bounds, paint);

            // draw text
            paint.setColor(Color.BLACK);
            paint.setAlpha(255);
            paint.setTextSize(12);
            paint.setFlags(Paint.ANTI_ALIAS_FLAG);
            canvas.drawText(callsign, 0, height, paint);

            // add marker
            BitmapDrawable drawableText = new BitmapDrawable(_context.getResources(), bitmap);
            BitmapDrawable drawableInfoIcon = new BitmapDrawable(_context.getResources(), bitmapInfoIcon);
            marker = new Marker(_mapView);
            marker.setId(callsign);
            marker.setIcon(drawableText);
            marker.setImage(drawableInfoIcon);
            marker.setOnMarkerClickListener((monitoredStationMarker, mapView) -> {
                GeoPoint markerPoint = monitoredStationMarker.getPosition();
                _infoWindow.open(monitoredStationMarker, new GeoPoint(markerPoint.getLatitude(), markerPoint.getLongitude()), 0, -2*height);
                _activeTrack.drawForStationMarker(monitoredStationMarker);
                return false;
            });
            _mapView.getOverlays().add(marker);
            _objectOverlayItems.put(callsign, marker);
        }

        marker.setPosition(new GeoPoint(group.getLatitude(), group.getLongitude()));
        marker.setTitle(newTitle);
        marker.setSnippet(newSnippet);

        return true;
    }

    private String getStatus(StationItem station) {
        double range = UnitTools.milesToKilometers(station.getRangeMiles());
        return String.format(Locale.US, "%s<br>%s %f %f<br>%03d° %03dkm/h %04dm %.2fkm<br>%s %s",
                station.getDigipath(),
                station.getMaidenHead(), station.getLatitude(), station.getLongitude(),
                (int)station.getBearingDegrees(),
                UnitTools.metersPerSecondToKilometersPerHour((int)station.getSpeedMetersPerSecond()),
                (int)station.getAltitudeMeters(),
                range == 0 ? UnitTools.milesToKilometers(Position.DEFAULT_RANGE_MILES): range,
                station.getStatus(),
                station.getComment());
    }
}
