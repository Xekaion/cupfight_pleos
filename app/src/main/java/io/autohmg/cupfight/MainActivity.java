package io.autohmg.cupfight;

import android.app.Activity;
import android.app.Dialog;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

import static io.autohmg.cupfight.DuelEngine.Driver.LOCAL;
import static io.autohmg.cupfight.DuelEngine.Driver.OPPONENT;
import static io.autohmg.cupfight.DuelEngine.EndReason.ROUTE_SPLIT;
import static io.autohmg.cupfight.DuelEngine.State.ACTIVE;
import static io.autohmg.cupfight.DuelEngine.State.IDLE;
import static io.autohmg.cupfight.DuelEngine.State.OFFERED;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xFF0A1018;
    private static final int SURFACE = 0xFF121D2A;
    private static final int LINE = 0xFF314457;
    private static final int WHITE = 0xFFF3F7FD;
    private static final int MUTED = 0xFFACBED1;
    private static final int MINT = 0xFF93E8F5;
    private static final int PEER = 0xFFCCC1FF;
    private static final int[][] ACTION_TEXT_STATES = {{-android.R.attr.state_enabled}, {}};
    private static final int DISABLED_TEXT = 0xFFB6C9DC;
    private static final ColorStateList PRIMARY_TEXT = new ColorStateList(ACTION_TEXT_STATES,
            new int[]{DISABLED_TEXT, BACKGROUND});
    private static final ColorStateList SECONDARY_TEXT = new ColorStateList(ACTION_TEXT_STATES,
            new int[]{DISABLED_TEXT, WHITE});
    private static final LightingColorFilter ON_PRIMARY_ICON = new LightingColorFilter(0xFF3B525E, 0);
    private static final int[] VIEW_RANGES = {50, 100, 150, 200};
    private static final int COMPLETION_DROP = 30;
    private static final int SPEED_PERMISSION_REQUEST = 1;
    private static final String SPEED_PERMISSION = "android.car.permission.CAR_SPEED";
    private static final long VEHICLE_SETTLEMENT_MILLIS = 1_500L;
    private static final long APPROACH_MILLIS = 5_200L;
    private static final long STOP_BEFORE_OFFER_MILLIS = 1_050L;
    private static final long PEER_REPLY_MILLIS = 1_650L;
    private static final String LOCAL_DRIVER = "local";
    private static final String DEMO_DRIVER = "simulated-peer";
    private static final String[] SEGMENTS = {"강남대로", "테헤란로"};
    private static final String[] MODELS = {"아반떼", "아이오닉 5"};

    private final DuelEngine duel = new DuelEngine();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::stepDemo;
    private final Runnable settleVehicle = this::finishVehicleMeasurement;
    private final Runnable offerArrival = this::offerArrivalMatch;
    private final Runnable replyToArrival = () -> simulateAccept(DriveSimulation.Scenario.SAFE_BRAKE);
    private final VehicleMeasurement vehicleMeasurement = new VehicleMeasurement();
    private final VehicleStopGate vehicleStopGate = new VehicleStopGate();
    private final Runnable checkVehicleStop = new Runnable() {
        @Override public void run() {
            updateVehicleInteraction();
            handler.postDelayed(this, 100L);
        }
    };

    private SharedPreferences garage;
    private UiSkin uiSkin;
    private GarageInventory inventory;
    private SegmentRanking ranking;
    private WaterAudio waterAudio;
    private PleosVehicleSignals vehicleSignals;
    private PleosVehicleSignals.Gear vehicleGear = PleosVehicleSignals.Gear.UNKNOWN;
    private PleosVehicleSignals.Capability speedCapability = PleosVehicleSignals.Capability.CONNECTING;
    private PleosVehicleSignals.Capability warningCapability = PleosVehicleSignals.Capability.CONNECTING;
    private VehicleMeasurement.Result vehicleResult;
    private boolean platformAllowsInteraction;
    private boolean vehicleControlsAllowed;
    private boolean vehicleSettlementPending;
    private View vehicleLockScreen;
    private TextView vehicleLockMessage;
    private Button speedPermissionButton;
    private Dialog activeDialog;
    private RoadView road;
    private View parkedScreen;
    private View parkedDestinations;
    private Button simulationEntry;
    private FrameLayout matchScreen;
    private TextView localConfirmation;
    private TextView peerConfirmation;
    private Button matchAccept;
    private LiveDuelView liveDuel;
    private LinearLayout duelPanel;
    private TextView driveStatus;
    private TextView balanceLabel;
    private TextView rangeLabel;
    private Button zoomIn;
    private Button zoomOut;
    private Button soundButton;
    private List<DriveSimulation.ReplayFrame> settledReplay = Collections.emptyList();
    private final Bitmap[] catalogAtlases = new Bitmap[2];
    private final EnumMap<Cosmetic, Bitmap> catalogThumbnails = new EnumMap<>(Cosmetic.class);

    private boolean localStopped = true;
    private boolean opponentStopped;
    private boolean sharedRoute;
    private int zoom = 1;
    private boolean soundEnabled;
    private boolean recordSaved;
    private int segmentIndex;
    private int modelIndex;
    private DriveSimulation.Scenario demoScenario = DriveSimulation.Scenario.SAFE_BRAKE;
    private DriveSimulation drive;
    private ValueAnimator arrivalAnimation;
    private boolean arrivalSequence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uiSkin = new UiSkin(this);
        garage = getSharedPreferences("garage", MODE_PRIVATE);
        inventory = new GarageInventory(garage);
        soundEnabled = garage.getBoolean("soundEnabled", true);
        ranking = new SegmentRanking(getSharedPreferences("segment_ranking", MODE_PRIVATE));
        waterAudio = new WaterAudio(this);
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            vehicleSignals = new PleosVehicleSignals(this, new PleosVehicleSignals.Listener() {
                @Override
                public void onGearChanged(PleosVehicleSignals.Gear gear, long vehicleTimestampNanos) {
                    applyVehicleGear(gear);
                }

                @Override
                public void onSpeedChanged(float metersPerSecond, long vehicleTimestampNanos) {
                    long now = SystemClock.elapsedRealtimeNanos();
                    if (vehicleStopGate.onSpeed(metersPerSecond, vehicleTimestampNanos, now)) {
                        if (BuildConfig.DEBUG && vehicleStopGate.isMoving(now)
                                && !vehicleMeasurement.isRunning()) {
                            vehicleResult = null;
                            vehicleMeasurement.start();
                        }
                        vehicleMeasurement.onSpeed(metersPerSecond, vehicleTimestampNanos);
                    }
                    updateVehicleInteraction();
                }

                @Override
                public void onInteractionAllowedChanged(boolean allowed) {
                    platformAllowsInteraction = allowed;
                    updateVehicleInteraction();
                }

                @Override
                public void onForwardCollisionWarning(long vehicleTimestampNanos) {
                    vehicleMeasurement.onForwardCollisionWarning(vehicleTimestampNanos);
                }

                @Override
                public void onCapabilitiesChanged(PleosVehicleSignals.Capability gear,
                                                  PleosVehicleSignals.Capability speed,
                                                  PleosVehicleSignals.Capability forwardCollisionWarning) {
                    if (speed != PleosVehicleSignals.Capability.AVAILABLE) {
                        vehicleMeasurement.interruptSpeed();
                        vehicleStopGate.invalidate();
                    }
                    speedCapability = speed;
                    warningCapability = forwardCollisionWarning;
                    if (BuildConfig.DEBUG) {
                        Log.i("CupFightVehicle", "gear=" + gear + " speed=" + speed + " fcw=" + forwardCollisionWarning);
                    }
                    updateVehicleInteraction();
                    if (canInteract()) {
                        renderDuel();
                    }
                }
            });
        }
        parkedScreen = buildScreen();
        FrameLayout display = new FrameLayout(this);
        display.setBackgroundColor(Color.BLACK);
        display.addView(parkedScreen, new FrameLayout.LayoutParams(-1, -1));
        matchScreen = buildMatchScreen();
        display.addView(matchScreen, new FrameLayout.LayoutParams(-1, -1));
        if (vehicleSignals != null) {
            vehicleLockScreen = buildVehicleLockScreen();
            display.addView(vehicleLockScreen, new FrameLayout.LayoutParams(-1, -1));
        }
        if (BuildConfig.DEBUG && vehicleSignals == null) {
            liveDuel = new LiveDuelView(this);
            display.addView(liveDuel, new FrameLayout.LayoutParams(-1, -1));
        }
        setContentView(display);
        updateAppearance();
        refreshZoom();
        refreshStatus();
        renderDuel();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && vehicleSignals == null) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vehicleSignals != null) {
            vehicleSignals.start();
            handler.post(checkVehicleStop);
        }
        if (drive != null) {
            if (liveDuel != null) {
                liveDuel.setPlaying(duel.getState() == ACTIVE);
            }
            handler.post(tick);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(checkVehicleStop);
        if (arrivalSequence) {
            updateVehicles(true, false, false);
        }
        if (liveDuel != null) {
            liveDuel.setPlaying(false);
        }
        closeParkedDialog();
        if (vehicleSignals != null) {
            // Keep debug-only signal capture alive while the emulator's controls cover the app.
            if (!BuildConfig.DEBUG) {
                vehicleSignals.stop();
            }
            vehicleMeasurement.interruptSpeed();
            vehicleStopGate.invalidate();
            vehicleGear = PleosVehicleSignals.Gear.UNKNOWN;
            updateVehicleInteraction();
        }
        waterAudio.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(settleVehicle);
        handler.removeCallbacks(checkVehicleStop);
        if (vehicleSignals != null) {
            vehicleSignals.stop();
        }
        waterAudio.release();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SPEED_PERMISSION_REQUEST && vehicleSignals != null
                && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            vehicleSignals.stop();
            vehicleSignals.start();
        }
    }

    private boolean canInteract() {
        return arrivalAnimation == null && drive == null
                && (vehicleSignals == null || (platformAllowsInteraction
                && vehicleStopGate.isStopped(SystemClock.elapsedRealtimeNanos())));
    }

    private View buildVehicleLockScreen() {
        LinearLayout screen = column();
        screen.setGravity(Gravity.CENTER);
        screen.setBackgroundColor(BACKGROUND);
        screen.setPadding(dp(32), dp(32), dp(32), dp(32));
        vehicleLockMessage = label("차속 확인 중", 24, WHITE, true);
        vehicleLockMessage.setGravity(Gravity.CENTER);
        screen.addView(vehicleLockMessage, new LinearLayout.LayoutParams(-1, -2));
        speedPermissionButton = action("차속 접근 허용", true, R.drawable.ui_ic_permission,
                () -> requestPermissions(new String[]{SPEED_PERMISSION}, SPEED_PERMISSION_REQUEST));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(240), dp(64));
        size.topMargin = dp(24);
        screen.addView(speedPermissionButton, size);
        return screen;
    }

    private void updateVehicleInteraction() {
        // Car.createCar may deliver callbacks before the activity view is attached.
        if (vehicleSignals == null || vehicleLockScreen == null) {
            return;
        }
        boolean allowed = canInteract();
        boolean stopped = vehicleStopGate.isStopped(SystemClock.elapsedRealtimeNanos());
        if (!stopped) {
            handler.removeCallbacks(settleVehicle);
            vehicleSettlementPending = false;
        } else if (BuildConfig.DEBUG && vehicleMeasurement.isRunning()
                && !vehicleSettlementPending) {
            vehicleSettlementPending = true;
            handler.postDelayed(settleVehicle, VEHICLE_SETTLEMENT_MILLIS);
        }
        if (vehicleControlsAllowed != allowed) {
            vehicleControlsAllowed = allowed;
            if (!allowed) {
                duel.cancelOffer();
                waterAudio.stop();
            }
            refreshStatus();
            refreshZoom();
            renderDuel();
            if (BuildConfig.DEBUG) {
                Log.i("CupFightVehicle", "controls=" + allowed + " gear=" + vehicleGear
                        + " stopped=" + stopped + " platform=" + platformAllowsInteraction);
            }
        }
        updateParkedVisibility();
        boolean permissionRequired = speedCapability == PleosVehicleSignals.Capability.PERMISSION_REQUIRED;
        speedPermissionButton.setVisibility(permissionRequired ? View.VISIBLE : View.GONE);
        long now = SystemClock.elapsedRealtimeNanos();
        vehicleLockMessage.setText(permissionRequired ? "차속 접근 권한이 필요합니다."
                : !vehicleStopGate.isFresh(now) ? "차속 확인 중"
                : vehicleStopGate.isMoving(now) ? "주행 중에는 화면을 조작할 수 없습니다."
                : !stopped ? "정차 확인 중"
                : "차량 화면 사용이 제한되어 있습니다.");
    }

    private void updateParkedVisibility() {
        boolean parked = canInteract();
        boolean offerVisible = parked && duel.getState() == OFFERED;
        if (!parked || offerVisible != (matchScreen.getVisibility() == View.VISIBLE)) {
            closeParkedDialog();
        }
        if (offerVisible) {
            boolean accepted = duel.hasAccepted(LOCAL);
            updateConfirmation(localConfirmation, accepted);
            updateConfirmation(peerConfirmation, duel.hasAccepted(OPPONENT));
            if (matchAccept.isEnabled() == accepted) {
                setActionLabel(matchAccept, accepted ? "상대 수락 대기" : "수락", R.drawable.ui_ic_accept);
                styleAction(matchAccept, !accepted);
                matchAccept.setEnabled(!accepted);
            }
        }
        boolean arriving = arrivalAnimation != null;
        parkedDestinations.setVisibility(arriving ? View.INVISIBLE : View.VISIBLE);
        if (simulationEntry != null) {
            simulationEntry.setVisibility(arriving ? View.INVISIBLE : View.VISIBLE);
        }
        parkedScreen.setVisibility((parked || arriving) && !offerVisible ? View.VISIBLE : View.INVISIBLE);
        matchScreen.setVisibility(offerVisible ? View.VISIBLE : View.GONE);
        if (vehicleLockScreen != null) {
            vehicleLockScreen.setVisibility(parked ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (matchScreen.getVisibility() == View.VISIBLE) {
            reject();
        } else if (arrivalSequence) {
            updateVehicles(true, false, false);
        } else {
            super.onBackPressed();
        }
    }

    private void applyVehicleGear(PleosVehicleSignals.Gear gear) {
        if (gear == vehicleGear) {
            return;
        }
        vehicleGear = gear;
        if (BuildConfig.DEBUG) {
            Log.i("CupFightVehicle", "vehicle gear " + gear);
        }
        refreshStatus();
        renderDuel();
    }

    private void finishVehicleMeasurement() {
        vehicleSettlementPending = false;
        if (vehicleStopGate.isStopped(SystemClock.elapsedRealtimeNanos()) && vehicleMeasurement.isRunning()) {
            vehicleResult = vehicleMeasurement.finish();
            renderDuel();
        }
    }

    private View buildScreen() {
        LinearLayout screen = column();
        screen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF182938, BACKGROUND, 0xFF080C13}));
        screen.setPadding(dp(20), dp(14), dp(20), dp(16));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = artwork(R.drawable.ui_mark);
        header.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView title = label("컵파이트", 30, WHITE, true);
        title.setLetterSpacing(-0.025f);
        LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(0, dp(60), 1f);
        titleSize.leftMargin = dp(12);
        header.addView(title, titleSize);
        driveStatus = label("", 14, WHITE, true);
        driveStatus.setGravity(Gravity.CENTER);
        driveStatus.setPadding(dp(16), 0, dp(16), 0);
        driveStatus.setBackground(uiSkin.surface(20));
        header.addView(driveStatus, new LinearLayout.LayoutParams(-2, dp(40)));
        soundButton = action("", false, this::toggleSound);
        soundButton.setTextSize(14);
        LinearLayout.LayoutParams soundSize = new LinearLayout.LayoutParams(dp(116), dp(48));
        soundSize.leftMargin = dp(8);
        header.addView(soundButton, soundSize);
        balanceLabel = label("", 14, MINT, true);
        balanceLabel.setGravity(Gravity.CENTER);
        balanceLabel.setPadding(dp(8), 0, dp(8), 0);
        setReadoutIcon(balanceLabel, R.drawable.ui_ic_drop, 14);
        balanceLabel.setBackground(uiSkin.surface(20));
        LinearLayout.LayoutParams balanceSize = new LinearLayout.LayoutParams(dp(112), dp(40));
        balanceSize.leftMargin = dp(8);
        header.addView(balanceLabel, balanceSize);
        LinearLayout.LayoutParams headerSize = new LinearLayout.LayoutParams(-1, -2);
        headerSize.bottomMargin = dp(12);
        screen.addView(header, headerSize);

        LinearLayout content = row();
        screen.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        FrameLayout scene = new FrameLayout(this);
        scene.setBackground(outline(SURFACE, 24));
        scene.setClipToOutline(true);
        content.addView(scene, new LinearLayout.LayoutParams(0, -1, 1f));
        road = new RoadView(this);
        scene.addView(road, new FrameLayout.LayoutParams(-1, -1));
        road.setContentDescription("내 차와 주변 차량");

        TextView roadLabel = label("주변 차량", 13, WHITE, true);
        roadLabel.setGravity(Gravity.CENTER);
        roadLabel.setBackground(outline(0xED101A28, 17));
        FrameLayout.LayoutParams roadLabelSize =
                new FrameLayout.LayoutParams(dp(92), dp(34), Gravity.START | Gravity.TOP);
        roadLabelSize.setMargins(dp(18), dp(18), 0, 0);
        scene.addView(roadLabel, roadLabelSize);

        LinearLayout zoomTools = row();
        zoomTools.setGravity(Gravity.CENTER_VERTICAL);
        zoomTools.setBackground(uiSkin.surface(18));
        zoomTools.setPadding(dp(5), dp(5), dp(5), dp(5));
        rangeLabel = label("", 14, WHITE, true);
        rangeLabel.setGravity(Gravity.CENTER);
        zoomTools.addView(rangeLabel, new LinearLayout.LayoutParams(dp(100), dp(48)));
        zoomIn = action("", false, R.drawable.ui_ic_plus, () -> changeZoom(-1));
        zoomIn.setContentDescription("확대");
        zoomTools.addView(zoomIn, new LinearLayout.LayoutParams(dp(49), dp(48)));
        zoomOut = action("", false, R.drawable.ui_ic_minus, () -> changeZoom(1));
        zoomOut.setContentDescription("축소");
        LinearLayout.LayoutParams zoomOutSize = new LinearLayout.LayoutParams(dp(49), dp(48));
        zoomOutSize.leftMargin = dp(4);
        zoomTools.addView(zoomOut, zoomOutSize);
        FrameLayout.LayoutParams zoomSize =
                new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        zoomSize.setMargins(dp(10), 0, dp(12), dp(12));
        scene.addView(zoomTools, zoomSize);

        LinearLayout sidebar = column();
        int screenWidth = getResources().getConfiguration().screenWidthDp;
        LinearLayout.LayoutParams sidebarSize =
                new LinearLayout.LayoutParams(dp(screenWidth < 760 ? 218 : screenWidth < 1000 ? 260 : 314), -1);
        sidebarSize.leftMargin = dp(14);
        content.addView(sidebar, sidebarSize);

        ScrollView duelScroll = new ScrollView(this);
        duelScroll.setFillViewport(true);
        duelScroll.setBackground(uiSkin.surface(24));
        duelScroll.setScrollBarSize(dp(3));
        sidebar.addView(duelScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        duelPanel = column();
        duelPanel.setPadding(dp(20), dp(20), dp(20), dp(20));
        duelScroll.addView(duelPanel, new ScrollView.LayoutParams(-1, -2));

        LinearLayout destinations = row();
        addDestination(destinations, "상점", R.drawable.ui_ic_shop, true, () -> showCosmetics(true));
        addDestination(destinations, "차고", R.drawable.ui_ic_garage, false, () -> showCosmetics(false));
        addDestination(destinations, "랭킹", R.drawable.ui_ic_ranking, false, () -> showScopes(false));
        LinearLayout.LayoutParams destinationSize = new LinearLayout.LayoutParams(-1, -2);
        destinationSize.topMargin = dp(10);
        sidebar.addView(destinations, destinationSize);
        parkedDestinations = destinations;
        if (BuildConfig.DEBUG && vehicleSignals == null) {
            simulationEntry = action("시뮬레이션", false, R.drawable.ui_ic_sim, this::showSimulation);
            addAction(sidebar, simulationEntry);
        }
        return screen;
    }

    private FrameLayout buildMatchScreen() {
        FrameLayout screen = new FrameLayout(this);
        screen.setVisibility(View.GONE);
        screen.setAccessibilityPaneTitle("매칭 성사");
        screen.addView(new MatchBackdrop(this), new FrameLayout.LayoutParams(-1, -1));
        ImageView backdrop = artwork(R.drawable.ui_match_backdrop);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        screen.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        int width = getResources().getConfiguration().screenWidthDp;
        boolean compact = getResources().getConfiguration().screenHeightDp < 380;
        LinearLayout center = column();
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams centerSize = new FrameLayout.LayoutParams(
                dp(Math.min(448, width - 40)), -2, Gravity.CENTER);
        screen.addView(center, centerSize);

        ImageView icon = artwork(R.drawable.ui_mark);
        int iconSize = dp(compact ? 49 : 65);
        center.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView title = label("매칭 성사", compact ? 30 : 38, WHITE, true);
        title.setAccessibilityHeading(true);
        title.setGravity(Gravity.CENTER);
        addMatchRow(center, title, compact ? 7 : 12, -2);

        LinearLayout confirmations = row();
        localConfirmation = addConfirmation(confirmations, "내 차");
        peerConfirmation = addConfirmation(confirmations, "옆 차");
        addMatchRow(center, confirmations, compact ? 12 : 20, dp(compact ? 54 : 64));

        matchAccept = action("수락", true, R.drawable.ui_ic_accept, () -> accept(LOCAL));
        matchAccept.setTextSize(compact ? 20 : 22);
        addMatchRow(center, matchAccept, compact ? 10 : 16, dp(compact ? 51 : 62));

        LinearLayout actions = row();
        actions.setGravity(Gravity.CENTER);
        addHalfAction(actions, action("거절", false, R.drawable.ui_ic_reject, this::reject));
        if (BuildConfig.DEBUG && vehicleSignals == null) {
            addHalfAction(actions, action("시뮬레이션", false, R.drawable.ui_ic_sim, this::showSimulation));
        }
        addMatchRow(center, actions, 8, -2);
        return screen;
    }

    private void addMatchRow(LinearLayout parent, View row, int topMargin, int height) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, height);
        size.topMargin = dp(topMargin);
        parent.addView(row, size);
    }

    private TextView addConfirmation(LinearLayout parent, String name) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setBackground(uiSkin.surface(14));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1f);
        if (parent.getChildCount() > 0) {
            size.leftMargin = dp(8);
        }
        parent.addView(item, size);
        TextView heading = label(name, 13, WHITE, true);
        heading.setGravity(Gravity.CENTER);
        item.addView(heading);
        TextView state = label("응답 대기", 15, MUTED, true);
        state.setGravity(Gravity.CENTER);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        item.addView(state);
        return state;
    }

    private void updateConfirmation(TextView state, boolean accepted) {
        state.setText(accepted ? "수락 완료" : "응답 대기");
        state.setTextColor(accepted ? MINT : MUTED);
    }

    private void addDestination(LinearLayout destinations, String title, int icon,
                                boolean primary, Runnable open) {
        Button button = action(title, primary, open);
        Drawable mark = actionIcon(icon, 23, primary);
        button.setCompoundDrawablesRelative(null, mark, null, null);
        button.setCompoundDrawablePadding(dp(3));
        button.setTextSize(15);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(64), 1f);
        if (destinations.getChildCount() > 0) {
            size.leftMargin = dp(6);
        }
        destinations.addView(button, size);
    }

    private void changeZoom(int direction) {
        if (!canInteract()) {
            return;
        }
        zoom = Math.max(0, Math.min(VIEW_RANGES.length - 1, zoom + direction));
        refreshZoom();
    }

    private void refreshZoom() {
        int range = VIEW_RANGES[zoom];
        rangeLabel.setText("반경 " + range + "m");
        road.setRangeMeters(range);
        zoomIn.setEnabled(canInteract() && zoom > 0);
        zoomOut.setEnabled(canInteract() && zoom < VIEW_RANGES.length - 1);
    }

    private void updateVehicles(boolean ownStopped, boolean rivalStopped, boolean hasSharedRoute) {
        stopArrival();
        applyVehicleState(ownStopped, rivalStopped, hasSharedRoute);
    }

    private void applyVehicleState(boolean ownStopped, boolean rivalStopped, boolean hasSharedRoute) {
        localStopped = ownStopped;
        opponentStopped = rivalStopped;
        sharedRoute = hasSharedRoute;
        if (duel.getState() == OFFERED && !(localStopped && opponentStopped && sharedRoute)) {
            duel.cancelOffer();
        }
        if (canInteract() && duel.getState() == IDLE) {
            duel.offer(localStopped, opponentStopped, sharedRoute);
        }
        refreshStatus();
        renderDuel();
    }

    private void accept(DuelEngine.Driver driver) {
        if (!canInteract() || !(localStopped && opponentStopped && sharedRoute) || !duel.accept(driver)) {
            return;
        }
        if (duel.getState() == ACTIVE) {
            stopArrival();
            localStopped = false;
            opponentStopped = false;
            drive = new DriveSimulation(demoScenario);
            updateParkedVisibility();
            if (liveDuel != null) {
                liveDuel.start(duel.getRemaining(LOCAL), inventory.equippedIn(Cosmetic.Slot.CUP));
            }
            handler.post(tick);
        }
        refreshStatus();
        renderDuel();
    }

    private void reject() {
        if (duel.cancelOffer()) {
            stopArrival();
            refreshStatus();
            renderDuel();
        }
    }

    private void stepDemo() {
        if (drive == null) {
            return;
        }
        boolean running = duel.getState() == ACTIVE;
        int audioLevel = drive.advance(duel);
        if (running && liveDuel != null) {
            DriveSimulation.ReplayFrame frame = drive.liveFrame();
            liveDuel.setFrame(frame.localRemaining(), frame.localLateral(), frame.localBraking());
            if (duel.getState() != ACTIVE) {
                liveDuel.setPlaying(false);
            }
        }
        if (soundEnabled && audioLevel > 0) {
            waterAudio.play(audioLevel);
        }
        if (drive.hasParked()) {
            parkAndSettle();
        } else {
            handler.postDelayed(tick, DriveSimulation.FRAME_MILLIS);
        }
    }

    private void parkAndSettle() {
        settledReplay = drive.replayFrames();
        drive = null;
        if (liveDuel != null) {
            liveDuel.end();
        }
        waterAudio.stop();
        DuelEngine.Result result = duel.getResult();
        if (result.reason != ROUTE_SPLIT) {
            String segment = SEGMENTS[segmentIndex];
            String model = MODELS[modelIndex];
            ranking.record(segment, model, DEMO_DRIVER, "비교 차량", scoreTenths(result.opponentRemaining));
            recordSaved = ranking.record(segment, model, LOCAL_DRIVER, "내 기록", scoreTenths(result.localRemaining));
            if (recordSaved) {
                inventory.award(COMPLETION_DROP);
            }
        }
        localStopped = true;
        opponentStopped = true;
        refreshStatus();
        renderDuel();
    }

    private void resetDuel() {
        duel.reset();
        settledReplay = Collections.emptyList();
        recordSaved = false;
        updateVehicles(localStopped, opponentStopped, false);
    }

    private void refreshStatus() {
        driveStatus.setText(canInteract() ? "정차" : "주행 · 조작 제한");
        balanceLabel.setText("물방울 " + inventory.drops());
        setActionLabel(soundButton, soundEnabled ? "물소리 켜짐" : "물소리 꺼짐",
                soundEnabled ? R.drawable.ui_ic_sound : R.drawable.ui_ic_mute);
        soundButton.setEnabled(canInteract());
    }

    private void renderDuel() {
        updateParkedVisibility();
        duelPanel.removeAllViews();
        road.setMatchHighlight(duel.getState() == OFFERED);
        if (BuildConfig.DEBUG && vehicleSignals != null) {
            renderVehicleMeasurement();
            return;
        }
        switch (duel.getState()) {
            case IDLE:
                boolean arriving = arrivalAnimation != null;
                duelPanel.addView(label(arriving ? "주행 중" : "매칭 대기", 28, WHITE, true));
                addPanelDivider();
                addStatusRow("내 차", localStopped ? "정차" : "이동", MINT);
                addStatusRow("옆 차", arriving ? "이동" : opponentStopped ? "정차" : "대기", PEER);
                if (!arriving) {
                    addParkedPreviews();
                }
                break;
            case FINISHED:
                renderResult();
                break;
        }
    }

    private void renderVehicleMeasurement() {
        duelPanel.addView(label("차량 신호 테스트", 24, WHITE, true));
        addPanelText("차속  " + capabilityLabel(speedCapability), WHITE);
        addPanelText("전방충돌 경고  " + capabilityLabel(warningCapability), WHITE);
        if (speedCapability == PleosVehicleSignals.Capability.PERMISSION_REQUIRED) {
            addAction(duelPanel, action("차속 접근 허용", true, R.drawable.ui_ic_permission,
                    () -> requestPermissions(new String[]{SPEED_PERMISSION}, SPEED_PERMISSION_REQUEST)));
        }
        if (vehicleMeasurement.isRunning()) {
            addPanelText("기록 정산 중", MUTED);
        } else if (vehicleResult != null) {
            if (vehicleResult.hasScore) {
                addWaterResult(vehicleResult.remaining);
            } else {
                addPanelText("차속 기록이 부족합니다.", MUTED);
            }
            addPanelText("수신 경고  " + vehicleResult.warnings + "회", WHITE);
            addPanelText("세이프 브레이크  " + vehicleResult.protectedBrakes + "회", WHITE);
        }
        addParkedPreviews();
    }

    private void addParkedPreviews() {
        addAction(duelPanel, action("도로 회전 보기", false, R.drawable.ui_ic_steer, this::showRoadTurnPreview));
        addAction(duelPanel, action("물잔 미리보기", false, R.drawable.ui_ic_water, this::previewWater));
    }

    private static String capabilityLabel(PleosVehicleSignals.Capability capability) {
        return switch (capability) {
            case CONNECTING -> "연결 중";
            case AVAILABLE -> "연결됨";
            case UNSUPPORTED -> "지원되지 않음";
            case PERMISSION_REQUIRED -> "권한 필요";
            case UNAVAILABLE -> "연결되지 않음";
        };
    }

    private void renderResult() {
        DuelEngine.Result result = duel.getResult();
        String outcome = switch (result.outcome) {
            case LOCAL_WIN -> "승리";
            case OPPONENT_WIN -> "패배";
            case DRAW -> "무승부";
        };
        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label("대결 결과", 24, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView resultBadge = label(outcome, 14, result.outcome == DuelEngine.Outcome.LOCAL_WIN ? MINT : PEER, true);
        resultBadge.setGravity(Gravity.CENTER);
        resultBadge.setPadding(dp(12), 0, dp(12), 0);
        resultBadge.setBackground(outline(0xFF213448, 13));
        setReadoutIcon(resultBadge, R.drawable.ui_ic_finish, 14);
        heading.addView(resultBadge, new LinearLayout.LayoutParams(-2, dp(34)));
        duelPanel.addView(heading);
        addPanelText(scopeLabel(segmentIndex, modelIndex), MUTED);
        addWaterResult(result.localRemaining);
        LinearLayout metrics = row();
        addResultMetric(metrics, "상대 물잔", percent(scoreTenths(result.opponentRemaining)), PEER);
        addResultMetric(metrics, "세이프\n브레이크", duel.getProtectedBrakeCount(LOCAL) + "회", MINT);
        if (result.reason == ROUTE_SPLIT) {
            addResultMetric(metrics, "경로 분기", "종료", WHITE);
        } else if (recordSaved) {
            addResultMetric(metrics, "완주 보상", "물방울 +" + COMPLETION_DROP, MINT);
        }
        LinearLayout.LayoutParams metricsSize = new LinearLayout.LayoutParams(-1, -2);
        metricsSize.topMargin = dp(8);
        duelPanel.addView(metrics, metricsSize);
        LinearLayout actions = row();
        if (!settledReplay.isEmpty()) {
            Button replay = action("다시 보기", true, R.drawable.ui_ic_replay,
                    () -> showReplay(settledReplay, false));
            replay.setContentDescription("물잔 다시 보기");
            addHalfAction(actions, replay);
        }
        addHalfAction(actions, action("닫기", false, R.drawable.ui_ic_close, this::resetDuel));
        LinearLayout.LayoutParams actionsSize = new LinearLayout.LayoutParams(-1, -2);
        actionsSize.topMargin = dp(8);
        duelPanel.addView(actions, actionsSize);
        if (recordSaved) {
            addAction(duelPanel, action("이 구간 순위", false, R.drawable.ui_ic_ranking,
                    () -> showRanking(segmentIndex, modelIndex)));
        } else if (result.reason != ROUTE_SPLIT) {
            addPanelText("구간 기록을 저장하지 못했습니다.", MUTED);
        }
    }

    private void addResultMetric(LinearLayout row, String name, String value, int color) {
        LinearLayout item = column();
        item.setPadding(dp(6), dp(4), dp(4), dp(4));
        item.setBackground(uiSkin.surface(11));
        TextView title = label(name, 11, MUTED, true);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        title.setContentDescription(name.replace('\n', ' '));
        item.addView(title, new LinearLayout.LayoutParams(-1, dp(27)));
        TextView number = label(value, 13, color, true);
        number.setGravity(Gravity.CENTER);
        item.addView(number, new LinearLayout.LayoutParams(-1, dp(24)));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1f);
        if (row.getChildCount() > 0) {
            size.leftMargin = dp(4);
        }
        row.addView(item, size);
    }

    private void addStatusRow(String name, String value, int color) {
        LinearLayout status = row();
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.addView(label(name, 14, MUTED, false), new LinearLayout.LayoutParams(0, dp(36), 1f));
        status.addView(label(value, 15, color, true), new LinearLayout.LayoutParams(-2, dp(36)));
        duelPanel.addView(status, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addHalfAction(LinearLayout row, Button button) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(48), 1f);
        if (row.getChildCount() > 0) {
            size.leftMargin = dp(6);
        }
        button.setTextSize(14);
        row.addView(button, size);
    }

    private static int scoreTenths(float water) {
        return Math.round(water * 10f);
    }

    private static String percent(int tenths) {
        return String.format(Locale.KOREA, "%.1f%%", tenths / 10f);
    }

    private void showScopes(boolean chooseSimulation) {
        if (!canInteract()) {
            return;
        }
        LinearLayout choices = column();
        for (int segment = 0; segment < SEGMENTS.length; segment++) {
            LinearLayout group = column();
            group.setPadding(dp(14), dp(10), dp(14), dp(14));
            group.setBackground(uiSkin.surface(17));
            group.addView(label(SEGMENTS[segment], 17, WHITE, true), new LinearLayout.LayoutParams(-1, dp(32)));
            LinearLayout models = row();
            for (int model = 0; model < MODELS.length; model++) {
                int selectedSegment = segment;
                int selectedModel = model;
                Button choice = action(MODELS[model], false, R.drawable.ui_ic_car, () -> {
                    if (chooseSimulation) {
                        segmentIndex = selectedSegment;
                        modelIndex = selectedModel;
                        closeParkedDialog();
                    } else {
                        showRanking(selectedSegment, selectedModel);
                    }
                });
                choice.setContentDescription(scopeLabel(segment, model));
                addHalfAction(models, choice);
            }
            group.addView(models);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
            if (segment > 0) {
                size.topMargin = dp(10);
            }
            choices.addView(group, size);
        }
        showSheet(chooseSimulation ? "시연 구간과 차종" : "구간 랭킹", choices, 620, 380);
    }

    private String scopeLabel(int segment, int model) {
        return SEGMENTS[segment] + " · " + MODELS[model];
    }

    private void showRanking(int segment, int model) {
        if (!canInteract()) {
            return;
        }
        List<SegmentRanking.Entry> entries = ranking.standings(SEGMENTS[segment], MODELS[model]);
        LinearLayout board = column();
        TextView source = label("기기 내 최고 기록", 14, MUTED, true);
        setReadoutIcon(source, R.drawable.ui_ic_bookmark, 15);
        board.addView(source, new LinearLayout.LayoutParams(-1, dp(34)));
        if (entries.isEmpty()) {
            board.addView(label("이 구간과 차종의 주행 기록이 없습니다.", 17, WHITE, false),
                    new LinearLayout.LayoutParams(-1, dp(66)));
        }
        for (SegmentRanking.Entry entry : entries) {
            StringJoiner items = new StringJoiner(" · ");
            if (LOCAL_DRIVER.equals(entry.driverId)) {
                for (Cosmetic cosmetic : Cosmetic.values()) {
                    if (inventory.isEquipped(cosmetic) && cosmetic.slot != Cosmetic.Slot.CUP) {
                        items.add(cosmetic.label);
                    }
                }
            }
            LinearLayout result = row();
            result.setGravity(Gravity.CENTER_VERTICAL);
            result.setBackground(uiSkin.surface(18));
            result.setPadding(dp(14), dp(10), dp(16), dp(10));
            TextView place = label(entry.rank + "위", 17, MINT, true);
            place.setGravity(Gravity.CENTER);
            place.setBackground(shape(0xFF243B51, 12));
            result.addView(place, new LinearLayout.LayoutParams(dp(48), dp(44)));
            LinearLayout identity = column();
            identity.addView(label(entry.alias, 18, WHITE, true));
            if (items.length() > 0) {
                identity.addView(label(items.toString(), 13, MUTED, false));
            }
            LinearLayout.LayoutParams identitySize = new LinearLayout.LayoutParams(0, -2, 1f);
            identitySize.leftMargin = dp(14);
            result.addView(identity, identitySize);
            result.addView(label(percent(entry.remainingTenths), 27, WHITE, true));
            LinearLayout.LayoutParams rowSize = new LinearLayout.LayoutParams(-1, -2);
            rowSize.topMargin = dp(10);
            board.addView(result, rowSize);
        }
        addAction(board, action("다른 구간", false, R.drawable.ui_ic_route, () -> showScopes(false)));
        showSheet(scopeLabel(segment, model), board, 610, 380);
    }

    private void toggleSound() {
        if (canInteract()) {
            soundEnabled = !soundEnabled;
            garage.edit().putBoolean("soundEnabled", soundEnabled).apply();
            refreshStatus();
        }
    }

    private void showCosmetics(boolean shop) {
        showCosmetics(shop, Cosmetic.Category.ALL);
    }

    private void showCosmetics(boolean shop, Cosmetic.Category category) {
        if (!canInteract()) {
            return;
        }
        CatalogPanel panel = new CatalogPanel(shop, category);
        showSheet(shop ? "상점" : "차고", panel.body, 970, 626, panel::stopPreviewMotion);
    }

    private final class CatalogPanel {
        final LinearLayout body = column();
        private final LinearLayout catalog = column();
        private final LinearLayout filters = row();
        private final LinearLayout grid = column();
        private final TextView summary = label("", 16, MINT, true);
        private final List<Runnable> refreshCards = new ArrayList<>();
        private final boolean shop;
        private Cosmetic.Category category;
        private View preview;
        private Runnable refreshPreview;
        private TurnControls previewMotion;
        private int catalogScroll;

        CatalogPanel(boolean shop, Cosmetic.Category category) {
            this.shop = shop;
            this.category = category;
            body.setMinimumHeight(dp(getResources().getConfiguration().screenHeightDp < 500 ? 236 : 316));
            body.addView(catalog);
            LinearLayout top = row();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(summary, new LinearLayout.LayoutParams(0, -2, 1f));
            if (!shop) {
                addCatalogLink(top, "내 차 미리 보기", R.drawable.ui_ic_car, () -> showTurnPreview(true));
            }
            addCatalogLink(top, shop ? "차고 열기" : "상점 열기",
                    shop ? R.drawable.ui_ic_garage : R.drawable.ui_ic_shop, () -> showCosmetics(!shop));
            catalog.addView(top);
            LinearLayout.LayoutParams filterSize = new LinearLayout.LayoutParams(-1, -2);
            filterSize.topMargin = dp(8);
            catalog.addView(filters, filterSize);
            catalog.addView(grid);
            renderGrid();
        }

        private void addCatalogLink(LinearLayout row, String title, int icon, Runnable callback) {
            Button link = action(title, false, icon, callback);
            link.setTextSize(14);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, dp(48));
            size.leftMargin = dp(8);
            link.setPadding(dp(12), 0, dp(12), 0);
            row.addView(link, size);
        }

        private void renderGrid() {
            filters.removeAllViews();
            for (Cosmetic.Category choice : Cosmetic.Category.values()) {
                int icon = switch (choice) {
                    case ALL -> R.drawable.ui_ic_grid;
                    case BODY -> R.drawable.ui_ic_paint;
                    case PARTS -> R.drawable.ui_ic_wheel;
                    case EFFECTS -> R.drawable.ui_ic_sparkle;
                    case CUP -> R.drawable.ui_ic_cup;
                };
                Button filter = action(choice.label, choice == category, icon, () -> {
                    category = choice;
                    renderGrid();
                });
                filter.setSelected(choice == category);
                addHalfAction(filters, filter);
            }
            grid.removeAllViews();
            refreshCards.clear();
            List<Cosmetic> items = new ArrayList<>();
            for (Cosmetic item : Cosmetic.values()) {
                if ((shop || inventory.owns(item))
                        && (category == Cosmetic.Category.ALL || item.slot.category == category)) {
                    items.add(item);
                }
            }
            if (items.isEmpty()) {
                grid.addView(label("이 분류에 보유한 장식이 없습니다.", 16, MUTED, false),
                        new LinearLayout.LayoutParams(-1, dp(84)));
            }
            int columns = parkedScreen.getWidth() < dp(700) ? 2 : 3;
            for (int start = 0; start < items.size(); start += columns) {
                LinearLayout shelf = row();
                for (int offset = 0; offset < columns; offset++) {
                    int index = start + offset;
                    View card = index < items.size() ? cosmeticCard(items.get(index), this) : new View(MainActivity.this);
                    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1f);
                    size.leftMargin = offset == 0 ? 0 : dp(10);
                    shelf.addView(card, size);
                }
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
                size.topMargin = dp(10);
                grid.addView(shelf, size);
            }
            refresh();
        }

        private void refresh() {
            summary.setText(shop ? "보유 물방울 " + inventory.drops() + "개"
                    : "보유 장식 " + inventory.ownedCount() + "개");
            refreshCards.forEach(Runnable::run);
            if (refreshPreview != null) {
                refreshPreview.run();
            }
        }

        private void select(Cosmetic item) {
            if (!canInteract()) {
                return;
            }
            if (shop && inventory.owns(item)) {
                showCosmetics(false, item.slot.category);
            } else if (shop) {
                inventory.buy(item);
                refreshStatus();
                refresh();
            } else if (inventory.toggle(item)) {
                updateAppearance();
                refresh();
            }
        }

        private void openPreview(Cosmetic item) {
            if (!canInteract() || preview != null) {
                return;
            }
            ScrollView scroll = (ScrollView) body.getParent();
            catalogScroll = scroll.getScrollY();
            catalog.setVisibility(View.GONE);
            LinearLayout panel = column();
            Button back = action(shop ? "상점으로" : "차고로", false, R.drawable.ui_ic_back, this::closePreview);
            panel.addView(back, new LinearLayout.LayoutParams(dp(120), dp(48)));
            LinearLayout layout = row();
            LinearLayout.LayoutParams layoutSize = new LinearLayout.LayoutParams(-1, -2);
            layoutSize.topMargin = dp(10);
            panel.addView(layout, layoutSize);

            boolean cupItem = item.slot == Cosmetic.Slot.CUP;
            FrameLayout stage = productStage(!cupItem);
            int controlsHeight = cupItem ? 60 : 118;
            int height = Math.max(dp(144), Math.min(dp(440), scroll.getHeight() - dp(controlsHeight)));
            layout.addView(stage, new LinearLayout.LayoutParams(0, height, 1.7f));
            if (cupItem) {
                WaterCupView cup = new WaterCupView(MainActivity.this);
                cup.setCupStyle(item);
                cup.reset(72f);
                stage.addView(cup, new FrameLayout.LayoutParams(-1, -1));
            } else {
                RoadView car = new RoadView(MainActivity.this);
                car.setGaragePreview(true);
                car.setCosmetics(inventory.withPreview(item));
                stage.addView(car, new FrameLayout.LayoutParams(-1, -1));
                previewMotion = new TurnControls(car, true, item.slot == Cosmetic.Slot.GRILLE);
                addTurnControls(panel, previewMotion);
            }

            LinearLayout details = column();
            details.setPadding(dp(14), dp(12), dp(14), dp(12));
            details.setBackground(uiSkin.surface(16));
            LinearLayout.LayoutParams detailSize = new LinearLayout.LayoutParams(0, -1, 1f);
            detailSize.leftMargin = dp(10);
            layout.addView(details, detailSize);
            details.addView(label(item.label, 22, WHITE, true));
            TextView detail = label(item.detail, 15, MUTED, false);
            detail.setPadding(0, dp(10), 0, 0);
            details.addView(detail);
            if (item.animated) {
                TextView motion = label("애니메이션 재생 중", 14, MINT, true);
                motion.setPadding(0, dp(10), 0, 0);
                details.addView(motion);
            }
            details.addView(new View(MainActivity.this), new LinearLayout.LayoutParams(-1, 0, 1f));
            TextView status = label("", 15, MUTED, true);
            details.addView(status, new LinearLayout.LayoutParams(-1, dp(32)));
            Button control = action("", false, () -> select(item));
            addAction(details, control);
            refreshPreview = () -> updateCosmeticControl(item, shop, status, control);
            refreshPreview.run();
            preview = panel;
            body.addView(panel);
            body.post(() -> scroll.scrollTo(0, 0));
        }

        private void closePreview() {
            ScrollView scroll = (ScrollView) body.getParent();
            stopPreviewMotion();
            body.removeView(preview);
            preview = null;
            refreshPreview = null;
            catalog.setVisibility(View.VISIBLE);
            body.post(() -> scroll.scrollTo(0, catalogScroll));
        }

        private void stopPreviewMotion() {
            if (previewMotion != null) {
                previewMotion.stop();
                previewMotion = null;
            }
        }
    }

    private View cosmeticCard(Cosmetic item, CatalogPanel panel) {
        LinearLayout card = column();
        card.setBackground(uiSkin.surface(20));
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        boolean cupItem = item.slot == Cosmetic.Slot.CUP;
        FrameLayout stage = productStage(!cupItem);
        if (cupItem) {
            WaterCupView cup = new WaterCupView(this);
            cup.setCupStyle(item);
            cup.reset(72f);
            cup.setPlaying(false);
            cup.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            stage.addView(cup, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView preview = artwork(item.thumbnailResource);
            if (item.atlasIndex >= 0) {
                preview.setImageBitmap(catalogThumbnail(item));
            }
            preview.setPadding(dp(8), dp(18), dp(8), dp(5));
            stage.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        }
        addProductBadge(stage, item.slot.label, WHITE, Gravity.START);
        if (item.animated) {
            addProductBadge(stage, "움직임", MINT, Gravity.END);
        }
        int imageHeight = getResources().getConfiguration().screenHeightDp < 500 ? 92 : 126;
        card.addView(stage, new LinearLayout.LayoutParams(-1, dp(imageHeight)));
        TextView name = label(item.label, 18, WHITE, true);
        LinearLayout.LayoutParams nameSize = new LinearLayout.LayoutParams(-1, -2);
        nameSize.topMargin = dp(8);
        card.addView(name, nameSize);
        TextView status = label("", 14, MUTED, true);
        card.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout buttons = row();
        Button sample = action("미리 보기", false, R.drawable.ui_ic_play, () -> panel.openPreview(item));
        sample.setContentDescription(item.label + " 미리 보기");
        addHalfAction(buttons, sample);
        Button control = action("", false, () -> panel.select(item));
        addHalfAction(buttons, control);
        card.addView(buttons);
        panel.refreshCards.add(() -> updateCosmeticControl(item, panel.shop, status, control));
        return card;
    }

    private void updateCosmeticControl(Cosmetic item, boolean shop, TextView status, Button control) {
        boolean worn = inventory.isEquipped(item);
        boolean purchased = inventory.owns(item);
        status.setText(worn ? "장착 중" : purchased ? "보유 중" : "물방울 " + item.price + "개");
        status.setTextColor(worn ? MINT : MUTED);
        boolean affordable = inventory.drops() >= item.price;
        String actionName = shop ? (purchased ? "차고에서 보기" : affordable ? "구매" : "물방울 부족")
                : (worn ? "장착 해제" : "장착");
        int shopIcon = affordable ? R.drawable.ui_ic_buy : R.drawable.ui_ic_lock;
        int icon = shop ? (purchased ? R.drawable.ui_ic_garage : shopIcon)
                : (worn ? R.drawable.ui_ic_reject : R.drawable.ui_ic_equip);
        setActionLabel(control, actionName, icon);
        control.setContentDescription(item.label + " " + actionName);
        styleAction(control, shop ? !purchased && affordable : !worn);
        control.setEnabled(!shop || purchased || affordable);
    }

    private void addProductBadge(FrameLayout stage, String text, int color, int gravity) {
        TextView type = label(text, 12, color, true);
        type.setGravity(Gravity.CENTER);
        type.setPadding(dp(9), 0, dp(9), 0);
        type.setBackground(outline(0xE813202D, 11));
        FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(-2, dp(24), Gravity.TOP | gravity);
        badge.setMargins(dp(8), dp(8), dp(8), 0);
        stage.addView(type, badge);
    }

    private Bitmap catalogThumbnail(Cosmetic item) {
        Bitmap cached = catalogThumbnails.get(item);
        if (cached != null) {
            return cached;
        }
        int atlasIndex = item.atlasIndex / 5;
        if (catalogAtlases[atlasIndex] == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            catalogAtlases[atlasIndex] = BitmapFactory.decodeResource(getResources(),
                    atlasIndex == 0 ? R.drawable.cosmetics_parts_atlas : R.drawable.cosmetics_fx_atlas_v2, options);
        }
        Bitmap atlas = catalogAtlases[atlasIndex];
        if (atlas == null) {
            return null;
        }
        int tile = item.atlasIndex % 5;
        int width = atlas.getWidth() / 3;
        int height = atlas.getHeight() / 2;
        Bitmap thumbnail = Bitmap.createBitmap(atlas, tile % 3 * width, tile / 3 * height, width, height);
        catalogThumbnails.put(item, thumbnail);
        return thumbnail;
    }

    private FrameLayout productStage(boolean withShowroomImage) {
        FrameLayout stage = new FrameLayout(this);
        GradientDrawable light = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2D435A, 0xFF182637, 0xFF0C1520});
        light.setCornerRadius(dp(14));
        stage.setBackground(light);
        stage.setClipToOutline(true);
        if (withShowroomImage) {
            ImageView image = artwork(R.drawable.showroom_backdrop);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setAlpha(0.87f);
            stage.addView(image, new FrameLayout.LayoutParams(-1, -1));
        }
        return stage;
    }

    private void updateAppearance() {
        road.setCosmetics(inventory.equipped());
    }

    private void showRoadTurnPreview() {
        showTurnPreview(false);
    }

    private void showTurnPreview(boolean garagePreview) {
        if (!canInteract()) {
            return;
        }
        LinearLayout body = column();
        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(outline(SURFACE, 18));
        stage.setClipToOutline(true);
        RoadView scene = new RoadView(this);
        scene.setGaragePreview(garagePreview);
        scene.setCosmetics(inventory.equipped());
        scene.setRangeMeters(VIEW_RANGES[zoom]);
        stage.addView(scene, new FrameLayout.LayoutParams(-1, -1));
        int stageHeight = Math.max(dp(142), Math.min(dp(432), parkedScreen.getHeight() - dp(180)));
        body.addView(stage, new LinearLayout.LayoutParams(-1, stageHeight));
        TurnControls controls = new TurnControls(scene, garagePreview, false);
        addTurnControls(body, controls);
        showSheet(garagePreview ? "내 차 미리 보기" : "도로 회전 보기", body, 1040, 600, controls::stop);
    }

    private void addTurnControls(LinearLayout parent, TurnControls controls) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(10);
        parent.addView(controls.view, size);
    }

    private final class TurnControls {
        final LinearLayout view = row();
        private final RoadView scene;
        private final Button left;
        private final Button center;
        private final Button right;
        private final Button front;
        private final float sideAngle;
        private float angle;
        private ValueAnimator motion;

        TurnControls(RoadView scene, boolean garagePreview, boolean faceFront) {
            this.scene = scene;
            sideAngle = garagePreview ? 90f : 38f;
            left = action(garagePreview ? "왼쪽 측면" : "좌회전", false, R.drawable.ui_ic_left_turn,
                    () -> turn(sideAngle));
            center = action(garagePreview ? "후면" : "직진", true,
                    garagePreview ? R.drawable.ui_ic_car : R.drawable.ui_ic_straight, () -> turn(0));
            right = action(garagePreview ? "오른쪽 측면" : "우회전", false, R.drawable.ui_ic_right_turn,
                    () -> turn(-sideAngle));
            front = garagePreview ? action("전면", false, R.drawable.ui_ic_straight, () -> turn(-180f)) : null;
            addHalfAction(view, left);
            addHalfAction(view, center);
            addHalfAction(view, right);
            if (front != null) {
                addHalfAction(view, front);
            }
            if (faceFront) {
                selectAngle(-180f);
                applyAngle(-180f);
            } else {
                center.setSelected(true);
            }
        }

        private void turn(float target) {
            if (!canInteract()) {
                return;
            }
            stop();
            selectAngle(target);
            float delta = normalize(target - angle);
            if (delta == 0f || !ValueAnimator.areAnimatorsEnabled()) {
                applyAngle(target);
                return;
            }
            motion = ValueAnimator.ofFloat(angle, angle + delta);
            motion.setDuration(Math.round(220 + Math.abs(delta) * 3.2f));
            motion.setInterpolator(new AccelerateDecelerateInterpolator());
            motion.addUpdateListener(animation -> applyAngle((float) animation.getAnimatedValue()));
            motion.start();
        }

        private void selectAngle(float target) {
            select(left, target == sideAngle);
            select(center, target == 0f);
            select(right, target == -sideAngle);
            if (front != null) {
                select(front, target == -180f);
            }
        }

        private void select(Button button, boolean selected) {
            button.setSelected(selected);
            styleAction(button, selected);
        }

        private void applyAngle(float value) {
            angle = normalize(value);
            scene.setTurnPreview(angle);
        }

        private float normalize(float value) {
            return (value + 540f) % 360f - 180f;
        }

        private void stop() {
            if (motion != null) {
                motion.cancel();
                motion = null;
            }
        }
    }

    private void previewWater() {
        if (canInteract()) {
            showReplay(DriveSimulation.preview(), true);
        }
    }

    private void showReplay(List<DriveSimulation.ReplayFrame> frames, boolean preview) {
        if (!canInteract() || frames.size() < 2) {
            return;
        }
        ReplayPlayback playback = new ReplayPlayback(frames, preview);
        showSheet(preview ? "물잔 미리보기" : "물잔 다시 보기", playback.body, 870, 570, playback::stop);
        playback.play();
    }

    private final class ReplayPlayback {
        final LinearLayout body = column();
        private final List<DriveSimulation.ReplayFrame> frames;
        private final WaterCupView localCup = new WaterCupView(MainActivity.this);
        private final WaterCupView peerCup = new WaterCupView(MainActivity.this);
        private final TextView localScore = label("", 24, MINT, true);
        private final TextView peerScore = label("", 24, PEER, true);
        private final TextView event = label("", 15, WHITE, true);
        private final TextView time = label("", 13, MUTED, true);
        private final SeekBar seek = new SeekBar(MainActivity.this);
        private final Button control = action("재생", false, R.drawable.ui_ic_play, this::toggle);
        private final Runnable advance = this::advance;
        private final boolean preview;
        private long origin;
        private int index;
        private boolean playing;
        private boolean resumeAfterSeek;

        ReplayPlayback(List<DriveSimulation.ReplayFrame> frames, boolean preview) {
            this.frames = frames;
            this.preview = preview;
            localCup.setCupStyle(inventory.equippedIn(Cosmetic.Slot.CUP));
            localCup.setContentDescription("내 차 물잔 움직임");
            peerCup.setContentDescription("옆 차 물잔 움직임");

            LinearLayout pair = row();
            pair.addView(replayCup("내 차", localCup, localScore), new LinearLayout.LayoutParams(0, -1, 1f));
            LinearLayout.LayoutParams peer = new LinearLayout.LayoutParams(0, -1, 1f);
            peer.leftMargin = dp(12);
            pair.addView(replayCup("옆 차", peerCup, peerScore), peer);
            int stageHeight = getResources().getConfiguration().screenHeightDp < 500 ? 182 : 272;
            body.addView(pair, new LinearLayout.LayoutParams(-1, dp(stageHeight)));

            LinearLayout timeline = column();
            timeline.setBackground(uiSkin.surface(16));
            timeline.setPadding(dp(12), dp(7), dp(12), dp(6));
            LinearLayout status = row();
            status.setGravity(Gravity.CENTER_VERTICAL);
            status.addView(event, new LinearLayout.LayoutParams(0, dp(34), 1f));
            status.addView(time, new LinearLayout.LayoutParams(-2, dp(34)));
            timeline.addView(status);
            LinearLayout controls = row();
            controls.setGravity(Gravity.CENTER_VERTICAL);
            control.setTextSize(14);
            controls.addView(control, new LinearLayout.LayoutParams(dp(108), dp(48)));
            seek.setMax(frames.size() - 1);
            seek.setKeyProgressIncrement(5);
            seek.setContentDescription("재생 위치");
            seek.setProgressTintList(ColorStateList.valueOf(MINT));
            seek.setThumbTintList(null);
            seek.setThumb(uiSkin.replayThumb());
            controls.addView(seek, new LinearLayout.LayoutParams(0, dp(48), 1f));
            timeline.addView(controls);
            LinearLayout.LayoutParams timelineSize = new LinearLayout.LayoutParams(-1, -2);
            timelineSize.topMargin = dp(10);
            body.addView(timeline, timelineSize);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onStartTrackingTouch(SeekBar bar) {
                    resumeAfterSeek = playing;
                    stop();
                }

                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser) {
                        jumpTo(progress);
                        origin = SystemClock.uptimeMillis() - index * DriveSimulation.FRAME_MILLIS;
                        if (!playing) {
                            updateControl();
                        }
                    }
                }

                @Override public void onStopTrackingTouch(SeekBar bar) {
                    if (resumeAfterSeek && index < frames.size() - 1) {
                        play();
                    }
                }
            });
            jumpTo(0);
        }

        private FrameLayout replayCup(String name, WaterCupView cup, TextView score) {
            FrameLayout stage = productStage(false);
            stage.addView(cup, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout labels = row();
            labels.setGravity(Gravity.CENTER_VERTICAL);
            labels.setPadding(dp(14), 0, dp(14), 0);
            labels.addView(label(name, 16, WHITE, true), new LinearLayout.LayoutParams(0, -1, 1f));
            labels.addView(score, new LinearLayout.LayoutParams(-2, -1));
            stage.addView(labels, new FrameLayout.LayoutParams(-1, dp(48), Gravity.TOP));
            return stage;
        }

        private void jumpTo(int frameIndex) {
            index = frameIndex;
            int first = Math.max(0, index - 24);
            DriveSimulation.ReplayFrame initial = frames.get(first);
            localCup.reset(initial.localRemaining());
            peerCup.reset(initial.opponentRemaining());
            localCup.setFrame(initial.localRemaining(), initial.localLateral(), initial.localBraking());
            peerCup.setFrame(initial.opponentRemaining(), initial.opponentLateral(), 0f);
            for (int sample = first + 1; sample <= index; sample++) {
                DriveSimulation.ReplayFrame frame = frames.get(sample);
                localCup.restoreFrame(frame.localRemaining(), frame.localLateral(), frame.localBraking());
                peerCup.restoreFrame(frame.opponentRemaining(), frame.opponentLateral(), 0f);
            }
            updateReadout(frames.get(index));
        }

        private void play() {
            if (playing) {
                return;
            }
            if (index == frames.size() - 1) {
                jumpTo(0);
            }
            origin = SystemClock.uptimeMillis() - index * DriveSimulation.FRAME_MILLIS;
            playing = true;
            localCup.setPlaying(true);
            peerCup.setPlaying(true);
            updateControl();
            seek.postOnAnimation(advance);
        }

        private void toggle() {
            if (playing) {
                stop();
            } else {
                play();
            }
        }

        private void stop() {
            playing = false;
            seek.removeCallbacks(advance);
            localCup.setPlaying(false);
            peerCup.setPlaying(false);
            updateControl();
        }

        private void updateControl() {
            if (playing) {
                setActionLabel(control, "일시정지", R.drawable.ui_ic_pause);
            } else if (index == frames.size() - 1) {
                setActionLabel(control, "처음부터", R.drawable.ui_ic_replay);
            } else {
                setActionLabel(control, "재생", R.drawable.ui_ic_play);
            }
        }

        private void advance() {
            if (!playing || !canInteract() || !seek.isAttachedToWindow()) {
                stop();
                return;
            }
            int next = Math.min(frames.size() - 1,
                    (int) ((SystemClock.uptimeMillis() - origin) / DriveSimulation.FRAME_MILLIS));
            if (next > index && (next == frames.size() - 1 || next > index + 1)) {
                jumpTo(next);
            } else if (next > index) {
                DriveSimulation.ReplayFrame frame = frames.get(++index);
                localCup.setFrame(frame.localRemaining(), frame.localLateral(), frame.localBraking());
                peerCup.setFrame(frame.opponentRemaining(), frame.opponentLateral(), 0f);
                updateReadout(frame);
            }
            if (index == frames.size() - 1) {
                playing = false;
                updateControl();
            } else {
                seek.postOnAnimation(advance);
            }
        }

        private void updateReadout(DriveSimulation.ReplayFrame frame) {
            localScore.setText(percent(scoreTenths(frame.localRemaining())));
            peerScore.setText(percent(scoreTenths(frame.opponentRemaining())));
            seek.setProgress(index);
            double stepSeconds = DriveSimulation.FRAME_MILLIS / 1000.0;
            time.setText(String.format(Locale.KOREA, "%.1f / %.1f초", index * stepSeconds,
                    (frames.size() - 1) * stepSeconds));
            String cue = "주행";
            if (index == frames.size() - 1) {
                cue = "재생 완료";
            } else if (frame.protectedBrake()) {
                cue = "세이프 브레이크";
            } else if (frame.localBraking() < -2.5f) {
                cue = "급제동";
            } else if (frame.localLateral() != 0f || frame.opponentLateral() != 0f) {
                cue = "커브";
            }
            event.setText(preview ? "예시 주행 · " + cue : cue);
            event.setTextColor(frame.protectedBrake() ? MINT : WHITE);
        }
    }

    private void showSimulation() {
        List<DemoAction> actions = new ArrayList<>();
        switch (duel.getState()) {
            case IDLE:
                actions.add(new DemoAction("주행부터 대결까지", R.drawable.ui_ic_play, this::startArrival));
                actions.add(new DemoAction("두 차량 정차", R.drawable.ui_ic_park,
                        () -> updateVehicles(true, true, true)));
                actions.add(new DemoAction("내 차만 정차", R.drawable.ui_ic_car,
                        () -> updateVehicles(true, false, true)));
                actions.add(new DemoAction("공통 경로 없이 정차", R.drawable.ui_ic_route,
                        () -> updateVehicles(true, true, false)));
                actions.add(new DemoAction("구간·차종 선택", R.drawable.ui_ic_panel, () -> showScopes(true)));
                break;
            case OFFERED:
                if (!duel.hasAccepted(OPPONENT)) {
                    actions.add(new DemoAction("상대 수락 · 안전 제동", R.drawable.ui_ic_accept,
                            () -> simulateAccept(DriveSimulation.Scenario.SAFE_BRAKE)));
                    actions.add(new DemoAction("상대 수락 · 일반 급제동", R.drawable.ui_ic_steer,
                            () -> simulateAccept(DriveSimulation.Scenario.UNPROTECTED_BRAKE)));
                    actions.add(new DemoAction("상대 수락 · 경로 분기", R.drawable.ui_ic_route,
                            () -> simulateAccept(DriveSimulation.Scenario.ROUTE_SPLIT)));
                }
                actions.add(new DemoAction("상대 거절", R.drawable.ui_ic_reject, this::reject));
                actions.add(new DemoAction("상대 차량 출발", R.drawable.ui_ic_car,
                        () -> updateVehicles(true, false, true)));
                break;
            case FINISHED:
                actions.add(new DemoAction("새 대결", R.drawable.ui_ic_replay, this::resetDuel));
                break;
            default:
                return;
        }
        LinearLayout controls = column();
        for (DemoAction entry : actions) {
            addAction(controls, action(entry.label(), false, entry.icon(), () -> {
                closeParkedDialog();
                entry.command().run();
            }));
        }
        showSheet("시뮬레이션", controls, 565, 420);
    }

    private void simulateAccept(DriveSimulation.Scenario scenario) {
        if (duel.getState() != OFFERED || duel.hasAccepted(OPPONENT)) {
            return;
        }
        stopArrival();
        demoScenario = scenario;
        accept(OPPONENT);
    }

    private void startArrival() {
        if (!BuildConfig.DEBUG || vehicleSignals != null || !canInteract() || duel.getState() != IDLE) {
            return;
        }
        stopArrival();
        arrivalSequence = true;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        arrivalAnimation = animator;
        animator.setDuration(APPROACH_MILLIS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(frame -> road.setArrivalProgress((float) frame.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator finished) {
                if (arrivalAnimation != finished) {
                    return;
                }
                arrivalAnimation = null;
                road.setArrivalProgress(1f);
                refreshZoom();
                applyVehicleState(true, true, false);
                handler.postDelayed(offerArrival, STOP_BEFORE_OFFER_MILLIS);
            }
        });
        road.setArrivalProgress(0f);
        refreshZoom();
        applyVehicleState(false, false, false);
        animator.start();
    }

    private void offerArrivalMatch() {
        applyVehicleState(true, true, true);
        if (duel.getState() == OFFERED) {
            handler.postDelayed(replyToArrival, PEER_REPLY_MILLIS);
        }
    }

    private void stopArrival() {
        if (!arrivalSequence) {
            return;
        }
        arrivalSequence = false;
        handler.removeCallbacks(offerArrival);
        handler.removeCallbacks(replyToArrival);
        if (arrivalAnimation != null) {
            ValueAnimator running = arrivalAnimation;
            arrivalAnimation = null;
            running.cancel();
            road.setArrivalProgress(1f);
            refreshZoom();
        }
    }

    private record DemoAction(String label, int icon, Runnable command) {}

    private TextView addPanelText(String value, int color) {
        TextView view = label(value, 15, color, false);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(10);
        duelPanel.addView(view, size);
        return view;
    }

    private void addPanelDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(1));
        size.topMargin = dp(16);
        size.bottomMargin = dp(5);
        duelPanel.addView(divider, size);
    }

    private void addWaterResult(float water) {
        LinearLayout meter = column();
        meter.setPadding(dp(14), dp(8), dp(14), dp(10));
        meter.setBackground(uiSkin.surface(16));
        LinearLayout readout = row();
        readout.setGravity(Gravity.CENTER_VERTICAL);
        readout.addView(label("남은 물", 14, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1f));
        readout.addView(label(percent(scoreTenths(water)), 34, MINT, true));
        meter.addView(readout);
        ProgressBar fill = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        fill.setMax(1000);
        fill.setProgress(scoreTenths(water));
        fill.setProgressTintList(ColorStateList.valueOf(MINT));
        fill.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        fill.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        meter.addView(fill, new LinearLayout.LayoutParams(-1, dp(8)));
        LinearLayout.LayoutParams meterSize = new LinearLayout.LayoutParams(-1, -2);
        meterSize.topMargin = dp(10);
        duelPanel.addView(meter, meterSize);
    }

    private void showSheet(String title, View body, int preferredWidth, int preferredHeight) {
        showSheet(title, body, preferredWidth, preferredHeight, () -> {});
    }

    private void showSheet(String title, View body, int preferredWidth, int preferredHeight, Runnable onClose) {
        if (!canInteract()) {
            return;
        }
        closeParkedDialog();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = column();
        panel.setPadding(dp(20), dp(16), dp(20), dp(20));
        panel.setBackground(uiSkin.surface(26));
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(title, 25, WHITE, true);
        heading.setMinHeight(dp(48));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = action("닫기", false, R.drawable.ui_ic_close, dialog::dismiss);
        close.setMinHeight(dp(48));
        header.addView(close, new LinearLayout.LayoutParams(dp(78), -2));
        LinearLayout.LayoutParams headerSize = new LinearLayout.LayoutParams(-1, -2);
        headerSize.bottomMargin = dp(12);
        panel.addView(header, headerSize);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(panel);
        dialog.setOnDismissListener(ignored -> {
            onClose.run();
            if (activeDialog == dialog) {
                activeDialog = null;
            }
        });
        activeDialog = dialog;
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.67f);
        int width = Math.min(dp(preferredWidth), parkedScreen.getWidth() - dp(30));
        body.measure(View.MeasureSpec.makeMeasureSpec(width - dp(40), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = Math.min(body.getMeasuredHeight() + dp(96), Math.min(dp(preferredHeight),
                parkedScreen.getHeight() - dp(20)));
        dialog.show();
        window.setLayout(width, height);
        window.getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility());
    }

    private void closeParkedDialog() {
        if (activeDialog != null) {
            activeDialog.dismiss();
        }
    }

    private ImageView artwork(int resource) {
        ImageView image = new ImageView(this);
        image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    private void addAction(LinearLayout parent, Button button) {
        button.setMinHeight(dp(52));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(8);
        parent.addView(button, size);
    }

    private Button action(String title, boolean primary, Runnable callback) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundTintList(null);
        styleAction(button, primary);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        button.setOnClickListener(view -> callback.run());
        return button;
    }

    private Button action(String title, boolean primary, int icon, Runnable callback) {
        Button button = action(title, primary, callback);
        setActionLabel(button, title, icon);
        return button;
    }

    private void setActionLabel(Button button, String title, int icon) {
        // An inline image keeps the glyph and live label centered as a group.
        Drawable glyph = actionIcon(icon, 18, button.getTextColors().getDefaultColor() == BACKGROUND);
        SpannableString content = new SpannableString(title.isEmpty() ? "\uFFFC" : "\uFFFC\u00A0" + title);
        content.setSpan(new ImageSpan(glyph, DynamicDrawableSpan.ALIGN_CENTER), 0, 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(content);
        if (!title.isEmpty()) {
            button.setContentDescription(title);
        }
    }

    private void setReadoutIcon(TextView view, int icon, int size) {
        view.setCompoundDrawablesRelative(actionIcon(icon, size, false), null, null, null);
        view.setCompoundDrawablePadding(dp(5));
    }

    private Drawable actionIcon(int icon, int size, boolean primary) {
        Drawable drawable = getDrawable(icon).mutate();
        drawable.setBounds(0, 0, dp(size), dp(size));
        colorActionIcon(drawable, primary);
        return drawable;
    }

    private void colorActionIcon(Drawable icon, boolean primary) {
        if (icon != null) {
            icon.setColorFilter(primary ? ON_PRIMARY_ICON : null);
        }
    }

    private void styleAction(Button button, boolean primary) {
        button.setTextColor(primary ? PRIMARY_TEXT : SECONDARY_TEXT);
        button.setBackground(uiSkin.button(primary));
        if (button.getText() instanceof Spanned text) {
            for (ImageSpan span : text.getSpans(0, text.length(), ImageSpan.class)) {
                colorActionIcon(span.getDrawable(), primary);
            }
        }
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        return new LinearLayout(this);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable outline(int color, int radius) {
        GradientDrawable drawable = shape(color, radius);
        drawable.setStroke(dp(1), LINE);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
