#!/usr/bin/env python3
from pathlib import Path
import base64

root = Path("jellyfin-src")

# NOVA launch branding assets.
branding_dir = root / "app/src/main/res/drawable-nodpi"
branding_dir.mkdir(parents=True, exist_ok=True)
# The old full NOVA logo PNG is no longer used (all startup/home branding uses
# nova_mark). Do not generate it: AAPT2 can choke on the legacy embedded PNG
# during release resource compilation.
(branding_dir / "nova_app_icon.png").write_bytes(base64.b64decode("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"))

# Use a native vector for in-app NOVA branding.  This is intentionally
# independent of the PNG artwork because older Fire OS builds can fail to
# decode that PNG through Compose painterResource at runtime.
nova_mark = root / "app/src/main/res/drawable/nova_mark.xml"
nova_mark.write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="512dp"
    android:height="512dp"
    android:viewportWidth="512"
    android:viewportHeight="512">

    <!-- Clean Concept A NOVA mark.
         One solid path only: no overlays, highlights or intersecting shapes,
         so the N stays crisp on Fire TV / Android TV at every size. -->
    <path
        android:fillColor="#FFE50914"
        android:pathData="
            M155,105
            C130,105 110,126 110,151
            L110,360
            L185,405
            L185,250
            L324,365
            C347,384 376,375 387,353
            C390,347 392,339 392,329
            L392,145
            L315,95
            L315,256
            L197,159
            C182,147 168,138 155,135
            Z" />
</vector>
""", encoding="utf-8")

# Point all in-app logo surfaces at the safe vector.
for logo_user in [
    root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/SplashFragment.kt",
    root / "app/src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/Toolbar.kt",
    root / "app/src/main/java/org/jellyfin/androidtv/integration/dream/composable/DreamContentLogo.kt",
]:
    if logo_user.exists():
        q = logo_user.read_text(encoding="utf-8")
        q = q.replace("R.drawable.app_logo", "R.drawable.nova_mark")
        q = q.replace("R.drawable.nova_logo", "R.drawable.nova_mark")
        logo_user.write_text(q, encoding="utf-8")

# Build a proper NOVA splash using the vector mark plus the wordmark/tagline.
splash_file = root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/SplashFragment.kt"
if splash_file.exists():
    q = splash_file.read_text(encoding="utf-8")
    q = q.replace(
        "import androidx.compose.foundation.layout.Column\n",
        "import androidx.compose.foundation.layout.Column\nimport androidx.compose.foundation.layout.Spacer\n"
    )
    q = q.replace(
        "import androidx.compose.foundation.layout.fillMaxSize\n",
        "import androidx.compose.foundation.layout.fillMaxSize\nimport androidx.compose.foundation.layout.height\n"
    )
    q = q.replace(
        "import androidx.compose.ui.Alignment\n",
        "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.graphics.Color\n"
    )
    q = q.replace(
        "import androidx.compose.ui.unit.dp\n",
        "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\n"
    )
    q = q.replace(
        "import org.jellyfin.androidtv.ui.base.JellyfinTheme\n",
        "import org.jellyfin.androidtv.ui.base.JellyfinTheme\nimport org.jellyfin.androidtv.ui.base.Text\n"
    )
    old_splash_image = """\t\t\tImage(
\t\t\t\tpainter = painterResource(R.drawable.nova_mark),
\t\t\t\tcontentDescription = stringResource(R.string.app_name),
\t\t\t\tmodifier = Modifier
\t\t\t\t\t.width(400.dp)
\t\t\t\t\t.fillMaxHeight()
\t\t\t)
"""
    new_splash_image = """\t\t\tImage(
\t\t\t\tpainter = painterResource(R.drawable.nova_mark),
\t\t\t\tcontentDescription = stringResource(R.string.app_name),
\t\t\t\tmodifier = Modifier.width(240.dp)
\t\t\t)
\t\t\tSpacer(modifier = Modifier.height(14.dp))
\t\t\tText(
\t\t\t\ttext = "NOVA",
\t\t\t\tcolor = Color.White,
\t\t\t\tfontSize = 56.sp,
\t\t\t\tfontWeight = FontWeight.Bold,
\t\t\t\tletterSpacing = 6.sp,
\t\t\t)
\t\t\tText(
\t\t\t\ttext = "YOUR MEDIA",
\t\t\t\tcolor = Color(0xFFBDBDBD),
\t\t\t\tfontSize = 16.sp,
\t\t\t\tletterSpacing = 4.sp,
\t\t\t)
"""
    q = q.replace(old_splash_image, new_splash_image)
    splash_file.write_text(q, encoding="utf-8")

# Fire TV launcher banner using the NOVA logo on a black cinematic background.
nova_banner = root / "app/src/main/res/drawable/nova_app_banner.xml"
nova_banner.write_text("""<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#050505" />
        </shape>
    </item>
    <item android:left="70dp" android:right="70dp" android:top="8dp" android:bottom="8dp">
        <bitmap
            android:src="@drawable/nova_mark"
            android:gravity="center"
            android:tileMode="disabled" />
    </item>
</layer-list>
""", encoding="utf-8")

manifest = root / "app/src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace('android:banner="@mipmap/app_banner"', 'android:banner="@drawable/nova_app_banner"')
m = m.replace('android:icon="@mipmap/app_icon"', 'android:icon="@drawable/nova_mark"')
manifest.write_text(m, encoding="utf-8")

searchable = root / "app/src/main/res/xml/searchable.xml"
if searchable.exists():
    q = searchable.read_text(encoding="utf-8")
    q = q.replace('android:icon="@mipmap/app_icon"', 'android:icon="@drawable/nova_mark"')
    searchable.write_text(q, encoding="utf-8")

# Remove remaining hard-coded Jellyfin branding from startup/about UI.
select_server = root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/SelectServerFragment.kt"
if select_server.exists():
    q = select_server.read_text(encoding="utf-8")
    q = q.replace(
        'binding.appVersion.text = "jellyfin-androidtv ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}"',
        'binding.appVersion.text = "NOVA ${BuildConfig.VERSION_NAME}"'
    )
    select_server.write_text(q, encoding="utf-8")

about_screen = root / "app/src/main/java/org/jellyfin/androidtv/ui/settings/screen/about/SettingsAboutScreen.kt"
if about_screen.exists():
    q = about_screen.read_text(encoding="utf-8")
    q = q.replace('val heading = "Jellyfin app version"', 'val heading = "NOVA app version"')
    q = q.replace(
        'val caption = "jellyfin-androidtv ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}"',
        'val caption = "NOVA ${BuildConfig.VERSION_NAME}"'
    )
    about_screen.write_text(q, encoding="utf-8")


# ---------------------------------------------------------------------------
# NOVA first-run / setup experience
# ---------------------------------------------------------------------------
# Keep all of Jellyfin's discovery/authentication mechanics, but replace the
# legacy setup presentation with a cinematic NOVA TV onboarding flow.

drawable_dir = root / "app/src/main/res/drawable"
drawable_dir.mkdir(parents=True, exist_ok=True)

(drawable_dir / "nova_setup_background.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="0"
        android:startColor="#050505"
        android:centerColor="#090909"
        android:endColor="#141414" />
</shape>
""", encoding="utf-8")

(drawable_dir / "nova_setup_panel.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#D9141414" />
    <corners android:radius="10dp" />
    <stroke android:width="1dp" android:color="#333333" />
    <padding android:left="24dp" android:top="22dp" android:right="24dp" android:bottom="22dp" />
</shape>
""", encoding="utf-8")

(drawable_dir / "nova_setup_button.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false">
        <shape android:shape="rectangle">
            <solid android:color="#44222222" />
            <corners android:radius="5dp" />
        </shape>
    </item>
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="#E50914" />
            <corners android:radius="5dp" />
            <stroke android:width="2dp" android:color="#FF6670" />
        </shape>
    </item>
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="#B20710" />
            <corners android:radius="5dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#2B2B2B" />
            <corners android:radius="5dp" />
            <stroke android:width="1dp" android:color="#4B4B4B" />
        </shape>
    </item>
</selector>
""", encoding="utf-8")

(drawable_dir / "nova_setup_input.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <solid android:color="#202020" />
            <corners android:radius="5dp" />
            <stroke android:width="2dp" android:color="#FFFFFF" />
            <padding android:left="16dp" android:top="10dp" android:right="16dp" android:bottom="10dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#171717" />
            <corners android:radius="5dp" />
            <stroke android:width="1dp" android:color="#555555" />
            <padding android:left="16dp" android:top="10dp" android:right="16dp" android:bottom="10dp" />
        </shape>
    </item>
</selector>
""", encoding="utf-8")

styles = root / "app/src/main/res/values/styles.xml"
st = styles.read_text(encoding="utf-8")
if 'style name="Nova.Setup.Button"' not in st:
    st = st.replace(
        "</resources>",
        """
    <style name="Nova.Setup.Button" parent="android:Widget.Button">
        <item name="android:background">@drawable/nova_setup_button</item>
        <item name="android:textColor">#FFFFFFFF</item>
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textSize">15sp</item>
        <item name="android:textAllCaps">false</item>
        <item name="android:letterSpacing">0.02</item>
        <item name="android:paddingStart">22dp</item>
        <item name="android:paddingEnd">22dp</item>
        <item name="android:paddingTop">10dp</item>
        <item name="android:paddingBottom">10dp</item>
        <item name="android:minHeight">44dp</item>
        <item name="android:stateListAnimator">@null</item>
    </style>

    <style name="Nova.Setup.Input" parent="android:Widget.EditText">
        <item name="android:background">@drawable/nova_setup_input</item>
        <item name="android:textColor">#FFFFFFFF</item>
        <item name="android:textColorHint">#FF9A9A9A</item>
        <item name="android:fontFamily">sans-serif</item>
        <item name="android:textSize">16sp</item>
        <item name="android:singleLine">true</item>
        <item name="android:selectAllOnFocus">false</item>
    </style>
</resources>
"""
    )
styles.write_text(st, encoding="utf-8")

# Main server-selection page.
(root / "app/src/main/res/layout/fragment_select_server.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/nova_setup_background"
    android:orientation="vertical"
    android:paddingStart="54dp"
    android:paddingTop="20dp"
    android:paddingEnd="54dp"
    android:paddingBottom="28dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <ImageView
            android:layout_width="42dp"
            android:layout_height="42dp"
            android:contentDescription="@string/app_name"
            android:src="@drawable/nova_mark" />

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="10dp"
            android:orientation="vertical">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:fontFamily="sans-serif-medium"
                android:letterSpacing="0.18"
                android:text="NOVA"
                android:textColor="#FFFFFFFF"
                android:textSize="22sp"
                android:textStyle="bold" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:fontFamily="sans-serif"
                android:letterSpacing="0.16"
                android:text="YOUR MEDIA"
                android:textColor="#FF777777"
                android:textSize="8sp" />
        </LinearLayout>
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="20dp"
        android:layout_weight="1"
        android:orientation="horizontal">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_marginEnd="12dp"
            android:layout_weight="1"
            android:background="@drawable/nova_setup_panel"
            android:orientation="vertical">

            <TextView
                android:id="@+id/stored_servers_title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:fontFamily="sans-serif-medium"
                android:text="@string/saved_servers"
                android:textColor="#FFFFFFFF"
                android:textSize="24sp"
                android:textStyle="bold"
                android:visibility="gone"
                tools:visibility="visible" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/stored_servers"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_marginTop="12dp"
                android:layout_weight="1"
                app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
                tools:itemCount="3" />

            <TextView
                android:id="@+id/welcome_title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:fontFamily="sans-serif-medium"
                android:text="@string/welcome_title"
                android:textColor="#FFFFFFFF"
                android:textSize="34sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/welcome_content"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:layout_marginBottom="20dp"
                android:fontFamily="sans-serif"
                android:lineSpacingExtra="3dp"
                android:text="@string/welcome_content"
                android:textColor="#FFB3B3B3"
                android:textSize="16sp" />

            <Button
                android:id="@+id/enter_server_address"
                style="@style/Nova.Setup.Button"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/connect_manually_by_address" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:background="@drawable/nova_setup_panel"
            android:orientation="vertical">

            <androidx.compose.ui.platform.ComposeView
                android:id="@+id/notifications"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:descendantFocusability="blocksDescendants"
                android:focusable="false" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <TextView
                    android:id="@+id/discovery_title"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:fontFamily="sans-serif-medium"
                    android:text="@string/discovered_servers_title"
                    android:textColor="#FFFFFFFF"
                    android:textSize="24sp"
                    android:textStyle="bold" />

                <ProgressBar
                    android:id="@+id/discovery_progress_indicator"
                    android:layout_width="18dp"
                    android:layout_height="18dp" />
            </LinearLayout>

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/discovery_servers"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_marginTop="12dp"
                android:layout_weight="1"
                app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
                tools:itemCount="3" />

            <TextView
                android:id="@+id/discovery_none_found"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_marginTop="24dp"
                android:layout_weight="1"
                android:gravity="top|center_horizontal"
                android:text="@string/discovered_servers_empty"
                android:textColor="#FF8F8F8F"
                android:textSize="15sp"
                android:visibility="gone" />

            <TextView
                android:id="@+id/app_version"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="end"
                android:layout_marginTop="8dp"
                android:fontFamily="sans-serif"
                android:textColor="#FF666666"
                android:textSize="11sp"
                tools:text="NOVA version" />
        </LinearLayout>
    </LinearLayout>
</LinearLayout>
""", encoding="utf-8")

# Manual server-address screen.
(root / "app/src/main/res/layout/fragment_server_add.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/nova_setup_background"
    android:paddingHorizontal="54dp"
    android:paddingVertical="34dp">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="560dp"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical"
        android:background="@drawable/nova_setup_panel"
        android:padding="28dp">

        <TextView
            android:id="@+id/title"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:fontFamily="sans-serif-medium"
            android:text="@string/lbl_enter_server_address"
            android:textColor="#FFFFFFFF"
            android:textSize="34sp"
            android:textStyle="bold"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />

        <TextView
            android:id="@+id/address_label"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="14dp"
            android:fontFamily="sans-serif"
            android:text="@string/lbl_valid_server_address"
            android:textColor="#FFAAAAAA"
            android:textSize="15sp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/title" />

        <EditText
            android:id="@+id/address"
            style="@style/Nova.Setup.Input"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_marginTop="12dp"
            android:imeOptions="actionDone"
            android:inputType="textUri"
            android:nextFocusDown="@id/confirm"
            android:nextFocusForward="@id/confirm"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/address_label" />

        <Button
            android:id="@+id/confirm"
            style="@style/Nova.Setup.Button"
            android:layout_width="180dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="18dp"
            android:text="@string/action_connect"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/address" />

        <TextView
            android:id="@+id/error"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="14dp"
            android:fontFamily="sans-serif"
            android:textColor="#FFFF6B72"
            android:textSize="13sp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toBottomOf="@id/confirm"
            tools:text="@string/server_connecting" />
    </androidx.constraintlayout.widget.ConstraintLayout>
</FrameLayout>
""", encoding="utf-8")

# Who's watching / user selector.
(root / "app/src/main/res/layout/fragment_server.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/nova_setup_background"
    android:clipChildren="false"
    android:clipToPadding="false"
    android:paddingStart="54dp"
    android:paddingTop="26dp"
    android:paddingEnd="54dp"
    android:paddingBottom="30dp">

    <TextView
        android:id="@+id/notification"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:background="#FFB20710"
        android:gravity="center"
        android:padding="12dp"
        android:text="@string/server_unsupported_notification"
        android:textColor="#FFFFFFFF"
        android:visibility="gone"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        tools:visibility="gone" />

    <TextView
        android:id="@+id/title"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginTop="22dp"
        android:fontFamily="sans-serif-medium"
        android:gravity="center"
        android:text="@string/who_is_watching"
        android:textColor="#FFFFFFFF"
        android:textSize="38sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/notification"
        app:layout_goneMarginTop="0dp" />

    <androidx.leanback.widget.HorizontalGridView
        android:id="@+id/users"
        android:layout_width="match_parent"
        android:layout_height="174dp"
        android:layout_marginTop="18dp"
        android:clipChildren="false"
        android:clipToPadding="false"
        android:scrollbars="none"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/title"
        app:rowHeight="174dp"
        tools:itemCount="4" />

    <TextView
        android:id="@+id/no_users_warning"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:fontFamily="sans-serif"
        android:gravity="center"
        android:text="@string/no_user_warning"
        android:textColor="#FFAAAAAA"
        app:layout_constraintBottom_toBottomOf="@id/users"
        app:layout_constraintEnd_toEndOf="@id/actions_container"
        app:layout_constraintStart_toStartOf="@id/actions_container"
        app:layout_constraintTop_toTopOf="@id/users"
        tools:visibility="invisible" />

    <LinearLayout
        android:id="@+id/actions_container"
        android:layout_width="620dp"
        android:layout_height="wrap_content"
        android:layout_marginTop="22dp"
        android:gravity="center"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/users">

        <androidx.appcompat.widget.AppCompatButton
            android:id="@+id/add_user_button"
            style="@style/Nova.Setup.Button"
            android:layout_width="wrap_content"
            android:layout_height="48dp"
            android:layout_margin="8dp"
            android:drawableStart="@drawable/ic_user_add"
            android:drawablePadding="8dp"
            android:text="@string/add_user" />

        <org.jellyfin.androidtv.ui.ServerButtonView
            android:id="@+id/server_button"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_margin="8dp"
            android:layout_weight="1"
            android:padding="0dp" />
    </LinearLayout>

    <org.jellyfin.androidtv.ui.ExpandableTextView
        android:id="@+id/login_disclaimer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:fontFamily="sans-serif"
        android:gravity="center"
        android:maxLines="3"
        android:padding="8dp"
        android:textColor="#FF888888"
        android:textSize="12sp"
        app:layout_constraintEnd_toEndOf="@id/actions_container"
        app:layout_constraintStart_toStartOf="@id/actions_container"
        app:layout_constraintTop_toBottomOf="@id/actions_container"
        tools:text="Login disclaimer" />
</androidx.constraintlayout.widget.ConstraintLayout>
""", encoding="utf-8")

# Login shell.
(root / "app/src/main/res/layout/fragment_user_login.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/nova_setup_background">

    <LinearLayout
        android:id="@+id/login_panel"
        android:layout_width="560dp"
        android:layout_height="0dp"
        android:background="@drawable/nova_setup_panel"
        android:orientation="vertical"
        android:padding="30dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <TextView
            android:id="@+id/title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:fontFamily="sans-serif-medium"
            android:text="@string/lbl_sign_in"
            android:textColor="#FFFFFFFF"
            android:textSize="36sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:fontFamily="sans-serif"
            android:textColor="#FFAAAAAA"
            android:textSize="15sp"
            tools:text="@string/login_connect_to" />

        <androidx.fragment.app.FragmentContainerView
            android:id="@+id/login_method"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_marginTop="20dp"
            android:layout_weight="1"
            tools:name="org.jellyfin.androidtv.ui.startup.fragment.UserLoginCredentialsFragment" />

        <LinearLayout
            android:id="@+id/actions"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:fontFamily="sans-serif"
                android:text="@string/login_other_options"
                android:textColor="#FF8C8C8C"
                android:textSize="12sp"
                android:visibility="gone" />

            <Button
                android:id="@+id/use_credentials"
                style="@style/Nova.Setup.Button"
                android:layout_width="wrap_content"
                android:layout_height="42dp"
                android:layout_marginStart="8dp"
                android:text="@string/action_use_password"
                android:visibility="gone" />

            <Button
                android:id="@+id/use_quickconnect"
                style="@style/Nova.Setup.Button"
                android:layout_width="wrap_content"
                android:layout_height="42dp"
                android:layout_marginStart="8dp"
                android:text="@string/action_use_quickconnect"
                android:visibility="gone" />

            <Button
                android:id="@+id/cancel"
                style="@style/Nova.Setup.Button"
                android:layout_width="wrap_content"
                android:layout_height="42dp"
                android:layout_marginStart="8dp"
                android:text="@string/btn_cancel"
                tools:ignore="ButtonOrder" />
        </LinearLayout>
    </LinearLayout>

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="70dp"
        android:layout_marginEnd="70dp"
        android:gravity="center"
        android:orientation="vertical"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toEndOf="@id/login_panel"
        app:layout_constraintTop_toTopOf="parent">

        <ImageView
            android:layout_width="150dp"
            android:layout_height="150dp"
            android:contentDescription="@string/app_name"
            android:src="@drawable/nova_mark" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:fontFamily="sans-serif-medium"
            android:letterSpacing="0.20"
            android:text="NOVA"
            android:textColor="#FFFFFFFF"
            android:textSize="40sp"
            android:textStyle="bold" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:fontFamily="sans-serif"
            android:letterSpacing="0.17"
            android:text="YOUR MEDIA"
            android:textColor="#FF8A8A8A"
            android:textSize="11sp" />
    </LinearLayout>
</androidx.constraintlayout.widget.ConstraintLayout>
""", encoding="utf-8")

# Username/password form.
(root / "app/src/main/res/layout/fragment_user_login_credentials.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/username_label"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:fontFamily="sans-serif-medium"
        android:labelFor="@id/username"
        android:text="@string/input_username"
        android:textColor="#FFD0D0D0"
        android:textSize="13sp"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <EditText
        android:id="@+id/username"
        style="@style/Nova.Setup.Input"
        android:layout_width="0dp"
        android:layout_height="48dp"
        android:layout_marginTop="7dp"
        android:autofillHints="username"
        android:imeOptions="actionNext"
        android:inputType="text"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/username_label" />

    <TextView
        android:id="@+id/password_label"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="14dp"
        android:fontFamily="sans-serif-medium"
        android:labelFor="@id/password"
        android:text="@string/input_password"
        android:textColor="#FFD0D0D0"
        android:textSize="13sp"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/username" />

    <EditText
        android:id="@+id/password"
        style="@style/Nova.Setup.Input"
        android:layout_width="0dp"
        android:layout_height="48dp"
        android:layout_marginTop="7dp"
        android:autofillHints="password"
        android:imeOptions="actionDone"
        android:inputType="textPassword"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/password_label" />

    <Button
        android:id="@+id/confirm"
        style="@style/Nova.Setup.Button"
        android:layout_width="180dp"
        android:layout_height="44dp"
        android:layout_marginTop="18dp"
        android:text="@string/action_login"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/password" />

    <TextView
        android:id="@+id/error"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="14dp"
        android:fontFamily="sans-serif"
        android:textColor="#FFFF6B72"
        android:textSize="12sp"
        app:layout_constraintBottom_toBottomOf="@id/confirm"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toEndOf="@id/confirm"
        app:layout_constraintTop_toTopOf="@id/confirm" />
</androidx.constraintlayout.widget.ConstraintLayout>
""", encoding="utf-8")

# Quick Connect form.
(root / "app/src/main/res/layout/fragment_user_login_quick_connect.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:fontFamily="sans-serif"
        android:text="@string/login_quickconnect_step_1"
        android:textColor="#FFC8C8C8"
        android:textSize="14sp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:fontFamily="sans-serif"
        android:text="@string/login_quickconnect_step_2"
        android:textColor="#FFC8C8C8"
        android:textSize="14sp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:fontFamily="sans-serif"
        android:text="@string/login_quickconnect_step_3"
        android:textColor="#FFC8C8C8"
        android:textSize="14sp" />

    <TextView
        android:id="@+id/quick_connect_code"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="18dp"
        android:fontFamily="sans-serif-medium"
        android:letterSpacing="0.12"
        android:textColor="#FFE50914"
        android:textDirection="ltr"
        android:textSize="44sp"
        android:textStyle="bold" />

    <ProgressBar
        android:id="@+id/loading"
        style="@style/Widget.AppCompat.ProgressBar.Horizontal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:indeterminate="true" />

    <TextView
        android:id="@+id/error"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:fontFamily="sans-serif"
        android:textColor="#FFFF6B72"
        android:textSize="12sp" />
</LinearLayout>
""", encoding="utf-8")

# NOVA uses username/password only. Keep the upstream Quick Connect class/layout
# for compatibility, but never route users to it from the sign-in screen.
user_login = root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/UserLoginFragment.kt"
ul = user_login.read_text(encoding="utf-8")
old_initial_login = """		// Open initial fragment
		if (skipQuickConnect) setLoginMethod<UserLoginCredentialsFragment>()
		else setLoginMethod<UserLoginQuickConnectFragment>()"""
new_initial_login = """		// NOVA supports username/password sign-in only.
		setLoginMethod<UserLoginCredentialsFragment>()"""
if old_initial_login not in ul:
    raise RuntimeError("Could not find initial Quick Connect login routing")
ul = ul.replace(old_initial_login, new_initial_login, 1)
user_login.write_text(ul, encoding="utf-8")

# NOVA wording on first-run strings.
strings = root / "app/src/main/res/values/strings.xml"
sv = strings.read_text(encoding="utf-8")
sv = sv.replace(
    '<string name="welcome_title">Welcome to Jellyfin</string>',
    '<string name="welcome_title">Welcome to NOVA</string>'
)
sv = sv.replace(
    '<string name="welcome_content">The Free Software Media System</string>',
    '<string name="welcome_content">Your media universe, all in one place. Connect to your media server to begin.</string>'
)
sv = sv.replace(
    '1. Open the Jellyfin app on your phone or webbrowser, and sign in with your account',
    '1. Open your media server in a phone or web browser and sign in'
)
sv = sv.replace(
    '2. Open the user menu and go to the Quick Connect page',
    '2. Open the user menu and choose Quick Connect'
)
strings.write_text(sv, encoding="utf-8")

# Make server cards match the red/black NOVA setup language.
server_button = root / "app/src/main/java/org/jellyfin/androidtv/ui/ServerButtonView.kt"
sb = server_button.read_text(encoding="utf-8")
if "import androidx.compose.ui.graphics.Color" not in sb:
    sb = sb.replace(
        "import androidx.compose.ui.graphics.Shape\n",
        "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Shape\n"
    )
old_button_call = """		ServerButton(
			icon = {"""
new_button_call = """		ServerButton(
			icon = {"""
# Add explicit NOVA colors at the callsite through the composable function.
if "colors: ButtonColors = ButtonDefaults.colors()" not in sb:
    sb = sb.replace(
        "shape: Shape = ButtonDefaults.Shape,\n) {",
        """shape: Shape = ButtonDefaults.Shape,
	colors: org.jellyfin.androidtv.ui.base.button.ButtonColors = ButtonDefaults.colors(
		containerColor = Color(0xFF242424),
		contentColor = Color(0xFFE6E6E6),
		focusedContainerColor = Color(0xFFE50914),
		focusedContentColor = Color.White,
	),
) {"""
    )
    sb = sb.replace(
        "shape = shape,\n\t) {",
        "shape = shape,\n\t\tcolors = colors,\n\t) {",
        1
    )
server_button.write_text(sb, encoding="utf-8")

# Startup toolbar: NOVA identity on the left, just Help on the right.
startup_toolbar = root / "app/src/main/java/org/jellyfin/androidtv/ui/shared/toolbar/StartupToolbar.kt"
startup_toolbar.write_text("""package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.IconButton

@Composable
fun StartupToolbar(
    openHelp: () -> Unit,
    openSettings: () -> Unit,
) {
    Toolbar(
        start = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.nova_mark),
                    contentDescription = "NOVA",
                    modifier = Modifier.size(34.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "NOVA",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.2.sp,
                    )
                    Text(
                        text = "YOUR MEDIA",
                        color = Color(0xFF777777),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.15.sp,
                    )
                }
            }
        },
        end = {
            ToolbarButtons {
                IconButton(onClick = openHelp) {
                    Icon(
                        painter = painterResource(R.drawable.ic_help),
                        contentDescription = stringResource(R.string.help),
                    )
                }
                Spacer(Modifier.width(8.dp))
                ToolbarClock()
            }
        }
    )
}
""", encoding="utf-8")

# Connection help: keep useful behavior, replace old Jellyfin-doc visual with NOVA.
connect_help = root / "app/src/main/java/org/jellyfin/androidtv/ui/startup/fragment/ConnectHelpAlertFragment.kt"
ch = connect_help.read_text(encoding="utf-8")
ch = ch.replace("painterResource(R.drawable.qr_jellyfin_docs)", "painterResource(R.drawable.nova_mark)")
ch = ch.replace("colorResource(R.color.not_quite_black)", "Color(0xFF070707)")
if "import androidx.compose.ui.graphics.Color" not in ch:
    ch = ch.replace(
        "import androidx.compose.ui.focus.focusRequester\n",
        "import androidx.compose.ui.focus.focusRequester\nimport androidx.compose.ui.graphics.Color\n"
    )
ch = ch.replace("modifier = Modifier\n\t\t\t\t\t\t.width(200.dp)", "modifier = Modifier\n\t\t\t\t\t\t.width(180.dp)")
connect_help.write_text(ch, encoding="utf-8")


gradle = root / "app/build.gradle.kts"
text = gradle.read_text(encoding="utf-8")

text = text.replace(
    'applicationId = namespace',
    'applicationId = "app.nova.firetv"'
)

text = text.replace(
    'resValue("string", "app_id", namespace!!)',
    'resValue("string", "app_id", "app.nova.firetv")'
)
text = text.replace(
    'resValue("string", "app_search_suggest_authority", "${namespace}.content")',
    'resValue("string", "app_search_suggest_authority", "app.nova.firetv.content")'
)
text = text.replace(
    'resValue("string", "app_search_suggest_intent_data", "content://${namespace}.content/intent")',
    'resValue("string", "app_search_suggest_intent_data", "content://app.nova.firetv.content/intent")'
)

text = text.replace(
    'resValue("string", "app_id", namespace + applicationIdSuffix)',
    'resValue("string", "app_id", "app.nova.firetv.debug")'
)
text = text.replace(
    'resValue("string", "app_search_suggest_authority", "${namespace + applicationIdSuffix}.content")',
    'resValue("string", "app_search_suggest_authority", "app.nova.firetv.debug.content")'
)
text = text.replace(
    'resValue("string", "app_search_suggest_intent_data", "content://${namespace + applicationIdSuffix}.content/intent")',
    'resValue("string", "app_search_suggest_intent_data", "content://app.nova.firetv.debug.content/intent")'
)

text = text.replace(
    'base.archivesName.set("jellyfin-androidtv-v${project.getVersionName()}")',
    'base.archivesName.set("nova-tv-v${project.getVersionName()}")'
)

# Build a real release variant for NOVA v1. If no private release keystore is
# configured in CI, fall back to Android's debug signing key so the APK remains
# installable while BuildConfig.DEBUG stays false.
text = text.replace(
    'signingConfig = signingConfigs.findByName("release")',
    'signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")'
)

# AboutLibraries otherwise tries GitHub/SPDX lookups during every release build.
# NOVA does not need live license metadata at build time; offline mode avoids
# network/rate-limit stalls and makes CI deterministic and faster.
if "aboutLibraries {" not in text:
    text += """

aboutLibraries {
    offlineMode = true
}
"""

gradle.write_text(text, encoding="utf-8")

strings = root / "app/src/main/res/values/strings.xml"
s = strings.read_text(encoding="utf-8")
s = s.replace(
    '<string name="app_name_release" translatable="false" tools:ignore="UnusedResources">Jellyfin</string>',
    '<string name="app_name_release" translatable="false" tools:ignore="UnusedResources">NOVA</string>'
)
s = s.replace(
    '<string name="app_name_debug" translatable="false" tools:ignore="UnusedResources">Jellyfin Debug</string>',
    '<string name="app_name_debug" translatable="false" tools:ignore="UnusedResources">NOVA Debug</string>'
)
strings.write_text(s, encoding="utf-8")

# Replace remaining visible Jellyfin branding in the English resources used on
# this Fire TV build. Lower-case URLs/package names are intentionally left alone.
for localized_strings in [
    root / "app/src/main/res/values/strings.xml",
    root / "app/src/main/res/values-en-rGB/strings.xml",
]:
    if localized_strings.exists():
        localized_text = localized_strings.read_text(encoding="utf-8")
        localized_text = localized_text.replace("Jellyfin", "NOVA")
        localized_strings.write_text(localized_text, encoding="utf-8")

# The custom Home/category rails still contain the previous NOVA wordmark.
for branded_ui in [
    root / "app/src/main/java/org/jellyfin/androidtv/ui/home/HomeFragment.kt",
    root / "app/src/main/java/org/jellyfin/androidtv/ui/browsing/NovaSidebarHelper.kt",
]:
    if branded_ui.exists():
        branded_text = branded_ui.read_text(encoding="utf-8")
        branded_text = branded_text.replace('"NOVA"', '"NOVA"')
        branded_ui.write_text(branded_text, encoding="utf-8")


# Home-screen cinematic layout hooks.
home_rows = root / "app/src/main/java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt"
h = home_rows.read_text(encoding="utf-8")

# NOVA Home uses one simple row: 20 random movies. A new random set is
# requested each time the Home screen is created.
old_sections = "val homesections = userSettingPreferences.activeHomesections"
new_sections = """val homesections = emptyList<HomeSectionType>()"""
if old_sections not in h:
    raise RuntimeError("Could not find home section ordering hook")
h = h.replace(old_sections, new_sections, 1)

# Imports needed by the custom Random Movies row.
import_anchor = "import org.jellyfin.androidtv.data.model.DataRefreshService"
if import_anchor not in h:
    raise RuntimeError("Could not find HomeRowsFragment import anchor")
h = h.replace(
    import_anchor,
    """import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef""",
    1
)

sdk_import_anchor = "import org.jellyfin.sdk.api.client.ApiClient"
if sdk_import_anchor not in h:
    raise RuntimeError("Could not find HomeRowsFragment SDK import anchor")
h = h.replace(
    sdk_import_anchor,
    """import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest""",
    1
)

# Add exactly one Home content row after Jellyfin has finished processing its
# (now empty) configurable Home-section list.
rows_anchor = """			// Add sections to layout
			withContext(Dispatchers.Main) {"""
random_row = """			val randomMovies = GetItemsRequest(
				fields = ItemRepository.browseFields,
				imageTypeLimit = 1,
				enableTotalRecordCount = false,
				includeItemTypes = setOf(BaseItemKind.MOVIE),
				recursive = true,
				sortBy = setOf(ItemSortBy.RANDOM),
				limit = 20,
			)
			rows.add(
				HomeFragmentBrowseRowDefRow(
					BrowseRowDef("Random Movies", randomMovies, 20)
				)
			)

			// Add sections to layout
			withContext(Dispatchers.Main) {"""
if rows_anchor not in h:
    raise RuntimeError("Could not find HomeRowsFragment rows layout anchor")
h = h.replace(rows_anchor, random_row, 1)

# Do not add Jellyfin notification/audio rows above NOVA's Random Movies row.
old_special_rows = """				notificationsRow.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)
				nowPlaying.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)
				for (row in rows) row.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)"""
new_special_rows = """				for (row in rows) row.addToRowsAdapter(requireContext(), cardPresenter, adapter as MutableObjectAdapter<Row>)"""
if old_special_rows not in h:
    raise RuntimeError("Could not find HomeRowsFragment special-row hook")
h = h.replace(old_special_rows, new_special_rows, 1)


# Keep NOVA's 20 random Home movies stable while the user enters/exits playback.
# Playback progress emits UserDataChanged messages; refreshing the row there would
# execute ItemSortBy.RANDOM again and replace the whole set.
user_data_refresh = """				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)"""
user_data_stable = """				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshCurrentItem() }
					.launchIn(this)"""
if user_data_refresh not in h:
    raise RuntimeError("Could not find Home UserData refresh hook")
h = h.replace(user_data_refresh, user_data_stable, 1)

# Likewise, returning from the player should update the selected card only,
# not re-run the random query.
resume_refresh = """		if (!justLoaded) {
			//Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
			refreshCurrentItem()
			refreshRows()
		} else {
			justLoaded = false
		}"""
resume_stable = """		if (!justLoaded) {
			// Returning from playback: keep the same random movie set and only
			// refresh metadata/progress for the currently selected card.
			refreshCurrentItem()
		} else {
			justLoaded = false
		}"""
if resume_refresh not in h:
    raise RuntimeError("Could not find Home onResume row refresh hook")
h = h.replace(resume_refresh, resume_stable, 1)


# Fix D-pad navigation between horizontal rows and the Compose sidebar.
# Left should move within the row normally; only the first card hands focus to the rail.
field_anchor = """	private var justLoaded = true"""
field_replacement = """	private var justLoaded = true

	var onNavigateToSidebar: (() -> Unit)? = null"""
if field_anchor not in h:
    raise RuntimeError("Could not find HomeRowsFragment callback anchor")
h = h.replace(field_anchor, field_replacement, 1)

key_anchor = """	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {"""
key_replacement = """	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		verticalGridView.setOnKeyInterceptListener { event ->
			if (
				event.action == KeyEvent.ACTION_DOWN &&
				event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
				event.repeatCount == 0
			) {
				val item = currentItem
				val itemIndex = if (item != null) (currentRow?.adapter as? ItemRowAdapter)?.indexOf(item) ?: -1 else -1

				if (itemIndex == 0) {
					onNavigateToSidebar?.invoke()
					true
				} else {
					false
				}
			} else {
				false
			}
		}
	}

	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {"""
if key_anchor not in h:
    raise RuntimeError("Could not find HomeRowsFragment key navigation anchor")
h = h.replace(key_anchor, key_replacement, 1)


# Make home media rows uniform poster cards, larger, and image-only.
image_import_anchor = "import org.jellyfin.androidtv.constant.HomeSectionType"
if image_import_anchor not in h:
    raise RuntimeError("Could not find ImageType import anchor")
h = h.replace(
    image_import_anchor,
    image_import_anchor + "\nimport org.jellyfin.androidtv.constant.ImageType",
    1
)

old_presenter = "val cardPresenter = CardPresenter()"
if old_presenter not in h:
    raise RuntimeError("Could not find home CardPresenter() hook")
h = h.replace(old_presenter, "val cardPresenter = CardPresenter(false, ImageType.POSTER, 190)", 1)

old_clear = """if (item !is BaseRowItem) {
				currentItem = null
				//fill in default background"""
new_clear = """if (item !is BaseRowItem) {
				currentItem = null
				HomeHeroStateStore.clear()
				//fill in default background"""
if old_clear not in h:
    raise RuntimeError("Could not find hero clear hook")
h = h.replace(old_clear, new_clear, 1)

old_background = """				backgroundService.setBackground(item.baseItem)"""
new_background = """				HomeHeroStateStore.update(item.baseItem)
				backgroundService.setBackground(item.baseItem)"""
if old_background not in h:
    raise RuntimeError("Could not find hero update hook")
h = h.replace(old_background, new_background, 1)

# Compose/Leanback focus changes can briefly report a non-ListRow selection.
# Avoid the old unchecked row cast so normal D-pad browsing cannot crash Home.
unsafe_home_selection = """			if (item !is BaseRowItem) {
				currentItem = null
				HomeHeroStateStore.clear()
				//fill in default background
				backgroundService.clearBackgrounds()
			} else {
				currentItem = item
				currentRow = row as ListRow"""
safe_home_selection = """			if (item !is BaseRowItem || row !is ListRow) {
				currentItem = null
				currentRow = null
				HomeHeroStateStore.clear()
				//fill in default background
				backgroundService.clearBackgrounds()
			} else {
				currentItem = item
				currentRow = row"""
if unsafe_home_selection not in h:
    raise RuntimeError("Could not find Home selection safety hook")
h = h.replace(unsafe_home_selection, safe_home_selection, 1)

home_rows.write_text(h, encoding="utf-8")

# v0.19.10 legacy media cards are customised below. Do not install a
# second focus listener here: Leanback already handles the normal focus scale,
# and LegacyImageCardView owns the NOVA red focus frame.
card_presenter = root / "app/src/main/java/org/jellyfin/androidtv/ui/presentation/CardPresenter.java"
c = card_presenter.read_text(encoding="utf-8")

# Force image-only POSTER presenters to keep video cards at a uniform 2:3
# aspect. For episodes, use the parent series poster instead of a 16:9 still.
old_episode_block = """                        case EPISODE:
                            if (m instanceof BaseItemDtoBaseRowItem && ((BaseItemDtoBaseRowItem) m).getPreferSeriesPoster()) {
                                mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_tv);
                                aspect = ImageHelper.ASPECT_RATIO_2_3;
                            } else {
                                mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_land_tv);
                                aspect = ImageHelper.ASPECT_RATIO_16_9;
                                if (itemDto.getLocationType() != null) {
                                    switch (itemDto.getLocationType()) {
                                        case FILE_SYSTEM:
                                            break;
                                        case REMOTE:
                                            break;
                                        case VIRTUAL:
                                            mCardView.setBanner(itemDto.getPremiereDate() == null || itemDto.getPremiereDate().isAfter(LocalDateTime.now()) ? R.drawable.banner_edge_future : R.drawable.banner_edge_missing);
                                            break;
                                    }
                                }
                                showProgress = true;
                                //Always show info for episodes
                                mCardView.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
                            }
                            break;"""

new_episode_block = """                        case EPISODE:
                            if (
                                imageType.equals(ImageType.POSTER) ||
                                (m instanceof BaseItemDtoBaseRowItem && ((BaseItemDtoBaseRowItem) m).getPreferSeriesPoster())
                            ) {
                                mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_port_tv);
                                aspect = ImageHelper.ASPECT_RATIO_2_3;
                                showProgress = true;
                            } else {
                                mDefaultCardImage = ContextCompat.getDrawable(mCardView.getContext(), R.drawable.tile_land_tv);
                                aspect = ImageHelper.ASPECT_RATIO_16_9;
                                if (itemDto.getLocationType() != null) {
                                    switch (itemDto.getLocationType()) {
                                        case FILE_SYSTEM:
                                            break;
                                        case REMOTE:
                                            break;
                                        case VIRTUAL:
                                            mCardView.setBanner(itemDto.getPremiereDate() == null || itemDto.getPremiereDate().isAfter(LocalDateTime.now()) ? R.drawable.banner_edge_future : R.drawable.banner_edge_missing);
                                            break;
                                    }
                                }
                                showProgress = true;
                                mCardView.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
                            }
                            break;"""

if old_episode_block not in c:
    raise RuntimeError("Could not find CardPresenter episode block")
c = c.replace(old_episode_block, new_episode_block, 1)

# Keep Nova home cards image-only and make artwork fill the full poster tile.
# Some episode cards force INFO_UNDER and recycled holders can then show title/
# rating text under movies too, so reset the card mode on every bind when this
# presenter was created with showInfo=false.
old_bind_block = """        holder.setItem(rowItem, mImageType, 130, 150, mStaticHeight);

        holder.mCardView.setTitleText(rowItem.getCardName(holder.mCardView.getContext()));
        holder.mCardView.setContentText(rowItem.getSubText(holder.mCardView.getContext()));"""
new_bind_block = """        holder.setItem(rowItem, mImageType, 130, 150, mStaticHeight);

        if (!mShowInfo) {
            holder.mCardView.setCardType(BaseCardView.CARD_TYPE_MAIN_ONLY);
            holder.mCardView.setTitleText(null);
            holder.mCardView.setContentText(null);
            holder.mCardView.getMainImageView().setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            holder.mCardView.setTitleText(rowItem.getCardName(holder.mCardView.getContext()));
            holder.mCardView.setContentText(rowItem.getSubText(holder.mCardView.getContext()));
        }"""
if old_bind_block not in c:
    raise RuntimeError("Could not find CardPresenter bind block")
c = c.replace(old_bind_block, new_bind_block, 1)

# For 16:9 Nova cards, never use a portrait poster as the first choice.
# Prefer a true landscape Thumb, then Backdrop, then parent landscape art.
# Episodes can use their own PRIMARY image because Jellyfin episode primary art
# is normally a 16:9 still frame.
old_image_selection = """        JellyfinImage image = null;
        if (rowItem.getBaseItem() != null) {
            if (aspect == ImageHelper.ASPECT_RATIO_BANNER) {
                image = JellyfinImageKt.getItemImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.BANNER);
            } else if (aspect == ImageHelper.ASPECT_RATIO_2_3 && rowItem.getBaseItem().getType() == BaseItemKind.EPISODE && rowItem instanceof BaseItemDtoBaseRowItem && ((BaseItemDtoBaseRowItem) rowItem).getPreferSeriesPoster()) {
                image = JellyfinImageKt.getSeriesPrimaryImage(rowItem.getBaseItem());
            } else if (aspect == ImageHelper.ASPECT_RATIO_16_9 && !isUserView && (rowItem.getBaseItem().getType() != BaseItemKind.EPISODE || !rowItem.getBaseItem().getImageTags().containsKey(org.jellyfin.sdk.model.api.ImageType.PRIMARY) || (rowItem.getPreferParentThumb() && rowItem.getBaseItem().getParentThumbImageTag() != null))) {
                if (rowItem.getPreferParentThumb() || !rowItem.getBaseItem().getImageTags().containsKey(org.jellyfin.sdk.model.api.ImageType.PRIMARY)) {
                    image = JellyfinImageKt.getParentImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.THUMB);
                } else {
                    image = JellyfinImageKt.getItemImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.THUMB);
                }
            } else {
                image = JellyfinImageKt.getItemImages(rowItem.getBaseItem()).get(org.jellyfin.sdk.model.api.ImageType.PRIMARY);
            }
        }"""

new_image_selection = """        JellyfinImage image = null;
        if (rowItem.getBaseItem() != null) {
            BaseItemDto baseItem = rowItem.getBaseItem();

            if (aspect == ImageHelper.ASPECT_RATIO_BANNER) {
                image = JellyfinImageKt.getItemImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.BANNER);
            } else if (
                aspect == ImageHelper.ASPECT_RATIO_2_3 &&
                baseItem.getType() == BaseItemKind.EPISODE
            ) {
                image = JellyfinImageKt.getSeriesPrimaryImage(baseItem);
            } else if (aspect == ImageHelper.ASPECT_RATIO_16_9 && !isUserView) {
                if (baseItem.getType() == BaseItemKind.EPISODE) {
                    image = JellyfinImageKt.getItemImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.PRIMARY);
                }

                if (image == null) {
                    image = JellyfinImageKt.getItemImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.THUMB);
                }

                if (image == null && !JellyfinImageKt.getItemBackdropImages(baseItem).isEmpty()) {
                    image = JellyfinImageKt.getItemBackdropImages(baseItem).get(0);
                }

                if (image == null) {
                    image = JellyfinImageKt.getParentImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.THUMB);
                }

                if (image == null && !JellyfinImageKt.getParentBackdropImages(baseItem).isEmpty()) {
                    image = JellyfinImageKt.getParentBackdropImages(baseItem).get(0);
                }
            } else {
                image = JellyfinImageKt.getItemImages(baseItem).get(org.jellyfin.sdk.model.api.ImageType.PRIMARY);
            }
        }"""

if old_image_selection not in c:
    raise RuntimeError("Could not find CardPresenter image selection block")
c = c.replace(old_image_selection, new_image_selection, 1)

card_presenter.write_text(c, encoding="utf-8")


# Style all library/category pages to match the Nova home screen.
browse_grid = root / "app/src/main/java/org/jellyfin/androidtv/ui/browsing/BrowseGridFragment.java"
b = browse_grid.read_text(encoding="utf-8")

# The sidebar occupies 188dp, so size the content grid to the remaining width.
old_grid_width = """        mGridWidth = Math.round(display.widthPixels / getResources().getDisplayMetrics().density);"""
new_grid_width = """        mGridWidth = Math.round(display.widthPixels / getResources().getDisplayMetrics().density - 188f);"""
if old_grid_width not in b:
    raise RuntimeError("Could not find BrowseGridFragment width hook")
b = b.replace(old_grid_width, new_grid_width, 1)

# Force a consistent poster-grid view on Nova category pages.
old_initial_prefs = """        mPosterSizeSetting = libraryPreferences.get(LibraryPreferences.Companion.getPosterSize());
        mImageType = libraryPreferences.get(LibraryPreferences.Companion.getImageType());
        mGridDirection = libraryPreferences.get(LibraryPreferences.Companion.getGridDirection());"""
new_initial_prefs = """        mPosterSizeSetting = PosterSize.MED;
        mImageType = ImageType.POSTER;
        mGridDirection = GridDirection.VERTICAL;"""
if old_initial_prefs not in b:
    raise RuntimeError("Could not find BrowseGridFragment initial preferences hook")
b = b.replace(old_initial_prefs, new_initial_prefs, 1)

old_resume_prefs = """        PosterSize posterSizeSetting = libraryPreferences.get(LibraryPreferences.Companion.getPosterSize());
        ImageType imageType = libraryPreferences.get(LibraryPreferences.Companion.getImageType());
        GridDirection gridDirection = libraryPreferences.get(LibraryPreferences.Companion.getGridDirection());"""
new_resume_prefs = """        PosterSize posterSizeSetting = PosterSize.MED;
        ImageType imageType = ImageType.POSTER;
        GridDirection gridDirection = GridDirection.VERTICAL;"""
if old_resume_prefs not in b:
    raise RuntimeError("Could not find BrowseGridFragment resume preferences hook")
b = b.replace(old_resume_prefs, new_resume_prefs, 1)

# Populate the same Home/Search/library/settings rail used on the Nova home page.
old_view_created = """        createGrid();
        loadGrid();
        addTools();"""
new_view_created = """        createGrid();
        loadGrid();
        addTools();
        NovaSidebarHelperKt.setupNovaSidebar(
            this,
            binding.novaSidebarItems,
            mFolder,
            userViewsRepository.getValue(),
            itemLauncher.getValue(),
            navigationRepository.getValue()
        );"""
if old_view_created not in b:
    raise RuntimeError("Could not find BrowseGridFragment onViewCreated hook")
b = b.replace(old_view_created, new_view_created, 1)

# Let Left move within the grid normally. Only when the selected card is in the
# first column should focus move into the currently active library button.
vertical_import = "import androidx.leanback.widget.VerticalGridPresenter;"
if vertical_import not in b:
    raise RuntimeError("Could not find VerticalGridPresenter import")

grid_focus_anchor = """        mGridView.setHorizontalSpacing(mGridItemSpacingHorizontal);
        mGridView.setVerticalSpacing(mGridItemSpacingVertical);
        mGridView.setFocusable(true);"""
grid_focus_replacement = """        mGridView.setHorizontalSpacing(mGridItemSpacingHorizontal);
        mGridView.setVerticalSpacing(mGridItemSpacingVertical);
        mGridView.setFocusable(true);

        mGridView.setOnKeyInterceptListener(event -> {
            if (
                event.getAction() == KeyEvent.ACTION_DOWN &&
                event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT &&
                mGridPresenter instanceof VerticalGridPresenter
            ) {
                int columns = Math.max(
                    1,
                    ((VerticalGridPresenter) mGridPresenter).getNumberOfColumns()
                );
                int position = mGridView.getSelectedPosition();

                if (position >= 0 && position % columns == 0) {
                    Object sidebarTarget = binding.novaSidebarItems.getTag();
                    if (sidebarTarget instanceof View) {
                        ((View) sidebarTarget).requestFocus();
                        return true;
                    }
                }
            }

            return false;
        });"""
if grid_focus_anchor not in b:
    raise RuntimeError("Could not find BrowseGridFragment focus hook")
b = b.replace(grid_focus_anchor, grid_focus_replacement, 1)

browse_grid.write_text(b, encoding="utf-8")

# Route Movies, TV Shows and Music through the same Nova library browser so all
# left-rail categories share one visual system instead of mixing Jellyfin screens.
item_launcher = root / "app/src/main/java/org/jellyfin/androidtv/ui/itemhandling/ItemLauncher.java"
i = item_launcher.read_text(encoding="utf-8")

old_user_view_switch = """        switch (collectionType) {
            case MOVIES:
            case TVSHOWS:
                LibraryPreferences displayPreferences = preferencesRepository.getValue().getLibraryPreferences(baseItem.getDisplayPreferencesId());
                boolean enableSmartScreen = displayPreferences.get(LibraryPreferences.Companion.getEnableSmartScreen());

                if (!enableSmartScreen) return Destinations.INSTANCE.libraryBrowser(baseItem);
                else return Destinations.INSTANCE.librarySmartScreen(baseItem);
            case MUSIC:
            case LIVETV:
                return Destinations.INSTANCE.librarySmartScreen(baseItem);
            default:
                return Destinations.INSTANCE.libraryBrowser(baseItem);
        }"""
new_user_view_switch = """        switch (collectionType) {
            case MOVIES:
            case TVSHOWS:
            case MUSIC:
                return Destinations.INSTANCE.libraryBrowser(baseItem);
            case LIVETV:
                return Destinations.INSTANCE.librarySmartScreen(baseItem);
            default:
                return Destinations.INSTANCE.libraryBrowser(baseItem);
        }"""
if old_user_view_switch not in i:
    raise RuntimeError("Could not find ItemLauncher user-view routing hook")
i = i.replace(old_user_view_switch, new_user_view_switch, 1)

item_launcher.write_text(i, encoding="utf-8")



# Convert category pages from dense poster grids into Home-style horizontal rows
# with selected-title info and the same Nova left navigation rail.
enhanced_browse = root / "app/src/main/java/org/jellyfin/androidtv/ui/browsing/EnhancedBrowseFragment.java"
e = enhanced_browse.read_text(encoding="utf-8")

user_views_import = "import org.jellyfin.androidtv.data.repository.CustomMessageRepository;"
if user_views_import not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment repository import anchor")
e = e.replace(
    user_views_import,
    """import org.jellyfin.androidtv.data.repository.CustomMessageRepository;
import org.jellyfin.androidtv.data.repository.UserViewsRepository;""",
    1
)

binding_fields = """    protected TextView mTitle;
    private LinearLayout mInfoRow;
    private TextView mSummary;"""
binding_fields_new = """    protected TextView mTitle;
    private LinearLayout mInfoRow;
    private TextView mSummary;
    private EnhancedDetailBrowseBinding binding;"""
if binding_fields not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment binding fields")
e = e.replace(binding_fields, binding_fields_new, 1)

inject_anchor = """    private final Lazy<CustomMessageRepository> customMessageRepository = inject(CustomMessageRepository.class);
    private final Lazy<NavigationRepository> navigationRepository = inject(NavigationRepository.class);"""
inject_new = """    private final Lazy<CustomMessageRepository> customMessageRepository = inject(CustomMessageRepository.class);
    private final Lazy<UserViewsRepository> userViewsRepository = inject(UserViewsRepository.class);
    private final Lazy<NavigationRepository> navigationRepository = inject(NavigationRepository.class);"""
if inject_anchor not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment injection anchor")
e = e.replace(inject_anchor, inject_new, 1)

local_binding = """        EnhancedDetailBrowseBinding binding = EnhancedDetailBrowseBinding.inflate(inflater, container, false);"""
if local_binding not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment local binding")
e = e.replace(
    local_binding,
    """        binding = EnhancedDetailBrowseBinding.inflate(inflater, container, false);""",
    1
)

view_created = """        setupEventListeners();"""
view_created_new = """        setupEventListeners();

        NovaSidebarHelperKt.setupNovaSidebar(
            this,
            binding.novaSidebarItems,
            mFolder,
            userViewsRepository.getValue(),
            itemLauncher.getValue(),
            navigationRepository.getValue()
        );

        androidx.leanback.widget.VerticalGridView novaRowsGrid = mRowsFragment.getVerticalGridView();\n        if (novaRowsGrid != null) novaRowsGrid.setOnKeyInterceptListener(event -> {
            if (
                event.getAction() == KeyEvent.ACTION_DOWN &&
                event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT &&
                mCurrentRow != null &&
                mCurrentItem != null &&
                mCurrentRow.getAdapter() instanceof ItemRowAdapter
            ) {
                ItemRowAdapter rowAdapter = (ItemRowAdapter) mCurrentRow.getAdapter();
                if (rowAdapter.indexOf(mCurrentItem) == 0) {
                    Object sidebarTarget = binding.novaSidebarItems.getTag();
                    if (sidebarTarget instanceof View) {
                        ((View) sidebarTarget).requestFocus();
                        return true;
                    }
                }
            }

            return false;
        });"""
if view_created not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment onViewCreated hook")
e = e.replace(view_created, view_created_new, 1)

destroy_view = """        mClickedListener.removeListeners();
        mSelectedListener.removeListeners();"""
destroy_view_new = """        mClickedListener.removeListeners();
        mSelectedListener.removeListeners();
        binding = null;"""
if destroy_view not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment destroy hook")
e = e.replace(destroy_view, destroy_view_new, 1)

card_presenter = """        mCardPresenter = new CardPresenter(false, 140);"""
card_presenter_new = """        mCardPresenter = new CardPresenter(false, ImageType.POSTER, 190);
        mCardPresenter.setUniformAspect(true);"""
if card_presenter not in e:
    raise RuntimeError("Could not find EnhancedBrowseFragment card presenter")
e = e.replace(card_presenter, card_presenter_new, 1)

enhanced_browse.write_text(e, encoding="utf-8")

browse_view = root / "app/src/main/java/org/jellyfin/androidtv/ui/browsing/BrowseViewFragment.java"
v = browse_view.read_text(encoding="utf-8")

movies_anchor = """            case MOVIES:
                itemType = BaseItemKind.MOVIE;"""
movies_new = """            case MOVIES:
                itemType = BaseItemKind.MOVIE;
                showViews = false;"""
if movies_anchor not in v:
    raise RuntimeError("Could not find MOVIES BrowseView hook")
v = v.replace(movies_anchor, movies_new, 1)

tv_anchor = """            case TVSHOWS:
                itemType = BaseItemKind.SERIES;"""
tv_new = """            case TVSHOWS:
                itemType = BaseItemKind.SERIES;
                showViews = false;"""
if tv_anchor not in v:
    raise RuntimeError("Could not find TVSHOWS BrowseView hook")
v = v.replace(tv_anchor, tv_new, 1)

music_anchor = """            case MUSIC:
                //Latest"""
music_new = """            case MUSIC:
                showViews = false;
                //Latest"""
if music_anchor not in v:
    raise RuntimeError("Could not find MUSIC BrowseView hook")
v = v.replace(music_anchor, music_new, 1)

movies_load = """                //Collections
                mRows.add(new BrowseRowDef(getString(R.string.lbl_collections), BrowsingUtils.createCollectionsRequest(mFolder.getId()), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}));

                rowLoader.loadRows(mRows);"""
movies_load_new = """                //Collections
                mRows.add(new BrowseRowDef(getString(R.string.lbl_collections), BrowsingUtils.createCollectionsRequest(mFolder.getId()), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}));

                //All movies as a horizontally scrolling poster row
                mRows.add(new BrowseRowDef(getString(R.string.lbl_all_items), BrowsingUtils.createBrowseGridItemsRequest(mFolder), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}));

                rowLoader.loadRows(mRows);"""
if movies_load not in v:
    raise RuntimeError("Could not find MOVIES loadRows hook")
v = v.replace(movies_load, movies_load_new, 1)

tv_load = """                //Favorites
                mRows.add(new BrowseRowDef(getString(R.string.lbl_favorites), BrowsingUtils.createFavoriteItemsRequest(mFolder.getId(), BaseItemKind.SERIES), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated, ChangeTriggerType.FavoriteUpdate}));

                rowLoader.loadRows(mRows);"""
tv_load_new = """                //Favorites
                mRows.add(new BrowseRowDef(getString(R.string.lbl_favorites), BrowsingUtils.createFavoriteItemsRequest(mFolder.getId(), BaseItemKind.SERIES), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated, ChangeTriggerType.FavoriteUpdate}));

                //All shows as a horizontally scrolling poster row
                mRows.add(new BrowseRowDef(getString(R.string.lbl_all_items), BrowsingUtils.createBrowseGridItemsRequest(mFolder), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}));

                rowLoader.loadRows(mRows);"""
if tv_load not in v:
    raise RuntimeError("Could not find TVSHOWS loadRows hook")
v = v.replace(tv_load, tv_load_new, 1)

music_load = """                //AudioPlaylists
                mRows.add(new BrowseRowDef(getString(R.string.lbl_playlists), BrowsingUtils.createPlaylistsRequest(), 60, false, true, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}, QueryType.AudioPlaylists));

                rowLoader.loadRows(mRows);"""
music_load_new = """                //AudioPlaylists
                mRows.add(new BrowseRowDef(getString(R.string.lbl_playlists), BrowsingUtils.createPlaylistsRequest(), 60, false, true, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}, QueryType.AudioPlaylists));

                //All music items in the same horizontal browsing style
                mRows.add(new BrowseRowDef(getString(R.string.lbl_all_items), BrowsingUtils.createBrowseGridItemsRequest(mFolder), 60, new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}));

                rowLoader.loadRows(mRows);"""
if music_load not in v:
    raise RuntimeError("Could not find MUSIC loadRows hook")
v = v.replace(music_load, music_load_new, 1)

default_anchor = """            default:
                boolean isRecordingsView = getArguments().getBoolean(Extras.IsLiveTvSeriesRecordings, false);"""
default_new = """            default:
                if (mFolder != null) {
                    showViews = false;
                    mRows.add(new BrowseRowDef(
                        getString(R.string.lbl_all_items),
                        BrowsingUtils.createBrowseGridItemsRequest(mFolder),
                        60,
                        new ChangeTriggerType[]{ChangeTriggerType.LibraryUpdated}
                    ));
                    rowLoader.loadRows(mRows);
                    break;
                }

                boolean isRecordingsView = getArguments().getBoolean(Extras.IsLiveTvSeriesRecordings, false);"""
if default_anchor not in v:
    raise RuntimeError("Could not find BrowseView default hook")
v = v.replace(default_anchor, default_new, 1)

browse_view.write_text(v, encoding="utf-8")

# All top-level libraries from the Nova rail now open the Home-style row screen.
# Live TV already uses that screen; other library types use its generic All Items row.
new_user_view_switch_rows = """        switch (collectionType) {
            case LIVETV:
                return Destinations.INSTANCE.librarySmartScreen(baseItem);
            default:
                return Destinations.INSTANCE.librarySmartScreen(baseItem);
        }"""
if new_user_view_switch not in i:
    raise RuntimeError("Could not find Nova ItemLauncher routing replacement")
i = i.replace(new_user_view_switch, new_user_view_switch_rows, 1)
item_launcher.write_text(i, encoding="utf-8")

# Route normal library/user-view screens into the NOVA cinematic library page.
destinations = root / "app/src/main/java/org/jellyfin/androidtv/ui/navigation/Destinations.kt"
d = destinations.read_text(encoding="utf-8")

dest_import = "import org.jellyfin.androidtv.ui.browsing.BrowseViewFragment"
if dest_import not in d:
    raise RuntimeError("Could not find Destinations BrowseViewFragment import")
d = d.replace(
    dest_import,
    dest_import + "\nimport org.jellyfin.androidtv.ui.browsing.NovaLibraryFragment",
    1
)

old_library_smart = """\tfun librarySmartScreen(item: BaseItemDto) = fragmentDestination<BrowseViewFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)"""
new_library_smart = """\tfun librarySmartScreen(item: BaseItemDto) = fragmentDestination<NovaLibraryFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)"""
if old_library_smart not in d:
    raise RuntimeError("Could not find librarySmartScreen destination")
d = d.replace(old_library_smart, new_library_smart, 1)

# Add the NOVA TV-series detail destination.
series_import = "import org.jellyfin.androidtv.ui.browsing.NovaLibraryFragment"
if series_import not in d:
    raise RuntimeError("Could not find NovaLibraryFragment import for series destination")
d = d.replace(
    series_import,
    series_import + "\nimport org.jellyfin.androidtv.ui.browsing.NovaSeriesFragment",
    1
)

item_details_anchor = """\tfun itemDetails(item: UUID) = fragmentDestination<FullDetailsFragment>(
\t\t"ItemId" to item.toString(),
\t)"""
series_destination = """\tfun novaSeries(item: BaseItemDto) = fragmentDestination<NovaSeriesFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)

""" + item_details_anchor
if item_details_anchor not in d:
    raise RuntimeError("Could not find itemDetails destination hook")
d = d.replace(item_details_anchor, series_destination, 1)

destinations.write_text(d, encoding="utf-8")

# Add a NOVA detail page for playable non-series items.
item_import = "import org.jellyfin.androidtv.ui.browsing.NovaSeriesFragment"
if item_import not in d:
    raise RuntimeError("Could not find NovaSeriesFragment import for item detail destination")
d = d.replace(
    item_import,
    item_import + "\nimport org.jellyfin.androidtv.ui.browsing.NovaItemFragment\nimport org.jellyfin.androidtv.ui.browsing.NovaInfoFragment",
    1
)

series_destination_anchor = """\tfun novaSeries(item: BaseItemDto) = fragmentDestination<NovaSeriesFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)
"""
if series_destination_anchor not in d:
    raise RuntimeError("Could not find novaSeries destination hook")
d = d.replace(
    series_destination_anchor,
    series_destination_anchor + """
\tfun novaItem(item: BaseItemDto) = fragmentDestination<NovaItemFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)

\tfun novaInfo(item: BaseItemDto) = fragmentDestination<NovaInfoFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t)

\tfun novaFilteredLibrary(
\t\titem: BaseItemDto,
\t\tfilterKind: String,
\t\tfilterValue: String?,
\t\ttitle: String,
\t) = fragmentDestination<NovaLibraryFragment>(
\t\tExtras.Folder to Json.Default.encodeToString(item),
\t\tNovaLibraryFragment.EXTRA_FILTER_KIND to filterKind,
\t\tNovaLibraryFragment.EXTRA_FILTER_VALUE to filterValue,
\t\tNovaLibraryFragment.EXTRA_FILTER_TITLE to title,
\t)

""",
    1
)

# Re-write Destinations after adding the generic item destination.
destinations.write_text(d, encoding="utf-8")

# Series cards now open the NOVA seasons/detail page instead of Jellyfin's legacy detail screen.
item_launcher = root / "app/src/main/java/org/jellyfin/androidtv/ui/itemhandling/ItemLauncher.java"
i = item_launcher.read_text(encoding="utf-8")

preview_controller_import = "import org.jellyfin.androidtv.ui.home.HomePreviewPlaybackController;"
playback_import_anchor = "import org.jellyfin.androidtv.ui.playback.MediaManager;"
if preview_controller_import not in i:
    if playback_import_anchor not in i:
        raise RuntimeError("Could not find ItemLauncher playback import anchor")
    i = i.replace(
        playback_import_anchor,
        preview_controller_import + "\n" + playback_import_anchor,
        1
    )

old_series_route = """                    case SERIES:
                    case MUSIC_ARTIST:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.itemDetails(baseItem.getId()));
                        return;"""
new_series_route = """                    case SERIES:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.novaSeries(baseItem));
                        return;
                    case MOVIE:
                    case VIDEO:
                        HomePreviewPlaybackController.INSTANCE.stopActivePreview();
                        // Home uses lightweight browse items that intentionally omit
                        // MediaSources/MediaStreams. retrieveAndPlay() refreshes the
                        // selected item from Jellyfin before launching playback, so
                        // PlaybackController always receives a complete media source.
                        playbackHelper.getValue().retrieveAndPlay(
                            baseItem.getId(),
                            false,
                            null,
                            context
                        );
                        return;
                    case MUSIC_ALBUM:
                    case PLAYLIST:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.novaItem(baseItem));
                        return;
                    case MUSIC_ARTIST:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.itemDetails(baseItem.getId()));
                        return;"""
if old_series_route not in i:
    raise RuntimeError("Could not find Series ItemLauncher route")
i = i.replace(old_series_route, new_series_route, 1)

old_music_route = """                    case MUSIC_ALBUM:
                    case PLAYLIST:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.itemList(baseItem.getId()));
                        return;

"""
if old_music_route not in i:
    raise RuntimeError("Could not find old music album/playlist route")
i = i.replace(old_music_route, "", 1)

# Episode cards should play immediately. Do not send the user through Jellyfin's
# legacy episode details submenu when selecting an episode from a season.
episode_anchor = """                    case SEASON:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.folderBrowser(baseItem));
                        return;"""
episode_direct_play = """                    case EPISODE:
                        HomePreviewPlaybackController.INSTANCE.stopActivePreview();
                        playbackHelper.getValue().retrieveAndPlay(
                            baseItem.getId(),
                            false,
                            null,
                            context
                        );
                        return;

                    case SEASON:
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.folderBrowser(baseItem));
                        return;"""
if episode_anchor not in i:
    raise RuntimeError("Could not find Season ItemLauncher route for direct episode playback")
i = i.replace(episode_anchor, episode_direct_play, 1)

item_launcher.write_text(i, encoding="utf-8")


# ---------------------------------------------------------------------------
# NOVA global remote focus / cursor
# ---------------------------------------------------------------------------
# Make focused controls red everywhere so the Fire TV remote position is always
# obvious, and remove Android's default white focus highlight from media cards.
# Force Android/Leanback focus and ripple highlights to NOVA red too.
theme_jellyfin = root / "app/src/main/res/values/theme_jellyfin.xml"
if theme_jellyfin.exists():
    tj = theme_jellyfin.read_text(encoding="utf-8")
    highlight_item = '<item name="android:colorControlHighlight">#FFE50914</item>'
    if 'android:colorControlHighlight' not in tj:
        theme_anchor = '<style name="Theme.Jellyfin" parent="@style/Theme.AppCompat.Leanback">'
        if theme_anchor in tj:
            tj = tj.replace(theme_anchor, theme_anchor + "\n        " + highlight_item, 1)
    else:
        import re
        tj = re.sub(
            r'<item name="android:colorControlHighlight">.*?</item>',
            highlight_item,
            tj,
            count=1,
        )
    theme_jellyfin.write_text(tj, encoding="utf-8")

color_scheme = root / "app/src/main/java/org/jellyfin/androidtv/ui/base/colorScheme.kt"
if color_scheme.exists():
    cs = color_scheme.read_text(encoding="utf-8")
    cs = cs.replace("buttonFocused = Color(0xE6CCCCCC)", "buttonFocused = Color(0xFFE50914)")
    cs = cs.replace("onButtonFocused = Color(0xFF444444)", "onButtonFocused = Color(0xFFFFFFFF)")
    cs = cs.replace("inputFocused = Color(0xE6CCCCCC)", "inputFocused = Color(0xFFE50914)")
    cs = cs.replace("onInputFocused = Color(0xFFDDDDDD)", "onInputFocused = Color(0xFFFFFFFF)")
    # Newer theme variants also expose list focus separately.
    cs = cs.replace("listButtonFocused = Tokens.Color.colorBluegrey800", "listButtonFocused = Color(0xFFE50914)")
    color_scheme.write_text(cs, encoding="utf-8")

# Jellyfin Android TV v0.19.10 uses CardPresenter.java. Patch that exact
# presenter so NOVA Search can use clean poster-only cards and every legacy
# media card gets the NOVA red focus outline.
legacy_card_presenter = root / "app/src/main/java/org/jellyfin/androidtv/ui/presentation/CardPresenter.java"
if legacy_card_presenter.exists():
    cpj = legacy_card_presenter.read_text(encoding="utf-8")

    # Add a per-presenter switch used only by Search. Existing constructor calls
    # retain the normal Jellyfin overlays.
    field_anchor = "    private boolean mShowInfo = true;\n"
    if field_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter show-info field")
    if "private boolean mShowOverlays" not in cpj:
        cpj = cpj.replace(
            field_anchor,
            field_anchor + "    private boolean mShowOverlays = true;\n",
            1,
        )

    constructor_anchor = """    public CardPresenter(boolean showInfo, ImageType imageType, int staticHeight) {
        this(showInfo, staticHeight);
        mImageType = imageType;
    }
"""
    constructor_nova = constructor_anchor + """
    public CardPresenter(boolean showInfo, ImageType imageType, int staticHeight, boolean showOverlays) {
        this(showInfo, imageType, staticHeight);
        mShowOverlays = showOverlays;
    }
"""
    if constructor_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter image constructor")
    if "boolean showOverlays)" not in cpj:
        cpj = cpj.replace(constructor_anchor, constructor_nova, 1)

    # Search poster-only cards should not draw watched/progress badges.
    user_data_anchor = """                    UserItemDataDto userData = itemDto.getUserData();
                    if (showWatched && userData != null) {"""
    user_data_nova = """                    if (!mShowOverlays) {
                        showWatched = false;
                        showProgress = false;
                    }
                    UserItemDataDto userData = itemDto.getUserData();
                    if (showWatched && userData != null) {"""
    if user_data_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter user-data overlay hook")
    cpj = cpj.replace(user_data_anchor, user_data_nova, 1)

    # Search should keep the poster image but omit title/footer metadata, rating
    # splats/stars, favourite marks and poster info overlays.
    overlay_anchor = """        if (ImageType.POSTER.equals(mImageType)) {
            holder.mCardView.setOverlayInfo(rowItem);
        }
        holder.mCardView.showFavIcon(rowItem.isFavorite());"""
    overlay_nova = """        if (mShowOverlays && ImageType.POSTER.equals(mImageType)) {
            holder.mCardView.setOverlayInfo(rowItem);
        }
        holder.mCardView.showFavIcon(mShowOverlays && rowItem.isFavorite());"""
    if overlay_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter poster overlay hook")
    cpj = cpj.replace(overlay_anchor, overlay_nova, 1)

    rating_anchor = """            if (rowItem.getBaseItem() != null && rowItem.getBaseItem().getType() != BaseItemKind.USER_VIEW) {"""
    rating_nova = """            if (mShowOverlays && rowItem.getBaseItem() != null && rowItem.getBaseItem().getType() != BaseItemKind.USER_VIEW) {"""
    if rating_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter rating hook")
    cpj = cpj.replace(rating_anchor, rating_nova, 1)

    # Disable Android/Leanback's default white focus highlight. The card view
    # itself draws the NOVA red frame so there is only one focus treatment.
    focus_anchor = """        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
"""
    focus_nova = """        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            cardView.setDefaultFocusHighlightEnabled(false);
        }
"""
    if focus_anchor not in cpj:
        raise RuntimeError("Could not find v0.19.10 CardPresenter focus hook")
    cpj = cpj.replace(focus_anchor, focus_nova, 1)

    legacy_card_presenter.write_text(cpj, encoding="utf-8")


# v0.19.10 media cards are LegacyImageCardView instances. Leanback can replace
# a normal OnFocusChangeListener after the presenter creates the card, so draw
# the NOVA focus frame inside the view's own onFocusChanged() callback instead.
legacy_image_card = root / "app/src/main/java/org/jellyfin/androidtv/ui/card/LegacyImageCardView.java"
if legacy_image_card.exists():
    lic = legacy_image_card.read_text(encoding="utf-8")

    constructor_focus_anchor = """        setForeground(null);
    }
"""
    constructor_focus_nova = """        setForeground(null);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setDefaultFocusHighlightEnabled(false);
        }
    }
"""
    if constructor_focus_anchor not in lic:
        raise RuntimeError("Could not find v0.19.10 LegacyImageCardView constructor focus hook")
    lic = lic.replace(constructor_focus_anchor, constructor_focus_nova, 1)

    focus_method_anchor = """    public void setBanner(int bannerResource) {"""
    focus_method_nova = """    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        if (gainFocus) {
            android.graphics.drawable.GradientDrawable focusFrame =
                    new android.graphics.drawable.GradientDrawable();
            focusFrame.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            focusFrame.setColor(android.graphics.Color.TRANSPARENT);
            final float density = getResources().getDisplayMetrics().density;
            int stroke = Math.max(2, Math.round(3f * density));
            focusFrame.setStroke(stroke, android.graphics.Color.rgb(229, 9, 20));

            // Match the foreground frame to the exact radius used by the poster.
            // This prevents poster-coloured corners showing outside the red frame.
            android.util.TypedValue rounding = new android.util.TypedValue();
            float cornerRadius = 6f * density;
            if (getContext().getTheme().resolveAttribute(
                    org.jellyfin.androidtv.R.attr.cardRounding,
                    rounding,
                    true
            )) {
                if (rounding.type == android.util.TypedValue.TYPE_DIMENSION) {
                    cornerRadius = android.util.TypedValue.complexToDimension(
                            rounding.data,
                            getResources().getDisplayMetrics()
                    );
                } else if (rounding.resourceId != 0) {
                    cornerRadius = getResources().getDimension(rounding.resourceId);
                }
            }
            focusFrame.setCornerRadius(cornerRadius);
            setForeground(focusFrame);
        } else {
            setForeground(null);
        }

        invalidate();
    }

    public void setBanner(int bannerResource) {"""
    if focus_method_anchor not in lic:
        raise RuntimeError("Could not find v0.19.10 LegacyImageCardView method anchor")
    if "protected void onFocusChanged(boolean gainFocus" not in lic:
        lic = lic.replace(focus_method_anchor, focus_method_nova, 1)

    legacy_image_card.write_text(lic, encoding="utf-8")


card_presenter = root / "app/src/main/java/org/jellyfin/androidtv/ui/presentation/CardPresenter.kt"
if card_presenter.exists():
    cp = card_presenter.read_text(encoding="utf-8")

    # v0.19.10 keeps the full CardPresenter constructor private. NOVA Search
    # needs one extra flag, so expose the primary constructor and add the flag
    # using a tail match that works across the supported source variants.
    cp = cp.replace("class CardPresenter private constructor(", "class CardPresenter(", 1)
    presenter_tail = """	val uniformAspect: Boolean,
) : Presenter() {"""
    presenter_tail_nova = """	val uniformAspect: Boolean,
	val showOverlays: Boolean = true,
) : Presenter() {"""
    if presenter_tail in cp and "val showOverlays: Boolean = true" not in cp:
        cp = cp.replace(presenter_tail, presenter_tail_nova, 1)

    # Search can request pure poster cards with no title/subtitle/rating/provider
    # overlays, while every other existing CardPresenter call keeps the upstream
    # behavior through the default showOverlays=True parameter.
    presenter_constructor = """class CardPresenter(
	val showInfo: Boolean,
	val imageType: ImageType,
	val staticHeight: Int,
	val uniformAspect: Boolean,
) : Presenter() {"""
    presenter_constructor_nova = """class CardPresenter(
	val showInfo: Boolean,
	val imageType: ImageType,
	val staticHeight: Int,
	val uniformAspect: Boolean,
	val showOverlays: Boolean = true,
) : Presenter() {"""
    if presenter_constructor in cp:
        cp = cp.replace(presenter_constructor, presenter_constructor_nova, 1)

    content_call_anchor = """					uniformAspect = uniformAspect,
				)"""
    content_call_nova = """					uniformAspect = uniformAspect,
					showOverlays = showOverlays,
				)"""
    if content_call_anchor in cp and "showOverlays = showOverlays" not in cp:
        cp = cp.replace(content_call_anchor, content_call_nova, 1)

    content_signature = """	staticHeight: Int,
	uniformAspect: Boolean,
) {"""
    content_signature_nova = """	staticHeight: Int,
	uniformAspect: Boolean,
	showOverlays: Boolean,
) {"""
    if content_signature in cp:
        cp = cp.replace(content_signature, content_signature_nova, 1)

    preview_anchor = """	val usePreview = displayConfig.overrideShowInfo ?: showInfo"""
    preview_nova = """	val usePreview = if (showOverlays) (displayConfig.overrideShowInfo ?: showInfo) else false"""
    if preview_anchor in cp:
        cp = cp.replace(preview_anchor, preview_nova, 1)

    overlay_anchor = """			overlay = {
				val showInfo = !usePreview && item.showCardInfoOverlay"""
    overlay_nova = """			overlay = {
				if (!showOverlays) return@ItemCard
				val showInfo = !usePreview && item.showCardInfoOverlay"""
    if overlay_anchor in cp:
        cp = cp.replace(overlay_anchor, overlay_nova, 1)

    if "import android.os.Build" not in cp:
        cp = cp.replace(
            "import android.view.KeyEvent\n",
            "import android.os.Build\nimport android.view.KeyEvent\n",
            1
        )

    if "import androidx.compose.foundation.border" not in cp:
        # CardPresenter versions differ slightly between Jellyfin releases;
        # insert beside another foundation import when available.
        if "import androidx.compose.foundation.Image\n" in cp:
            cp = cp.replace(
                "import androidx.compose.foundation.Image\n",
                "import androidx.compose.foundation.Image\nimport androidx.compose.foundation.border\n",
                1
            )
        elif "import androidx.compose.foundation.background\n" in cp:
            cp = cp.replace(
                "import androidx.compose.foundation.background\n",
                "import androidx.compose.foundation.background\nimport androidx.compose.foundation.border\n",
                1
            )

    focusable_anchor = """\t\t\tisFocusable = true
\t\t\tisFocusableInTouchMode = true"""
    focusable_replacement = """\t\t\tisFocusable = true
\t\t\tisFocusableInTouchMode = true
\t\t\tif (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
\t\t\t\tdefaultFocusHighlightEnabled = false
\t\t\t}"""
    if focusable_anchor in cp and "defaultFocusHighlightEnabled = false" not in cp:
        cp = cp.replace(focusable_anchor, focusable_replacement, 1)

    # Draw a strong red outline around whichever poster/card currently owns
    # focus. This is the visible Fire TV "cursor" while moving around.
    card_modifier_anchor = """\t\t\tmodifier = Modifier
\t\t\t\t.size(size)"""
    card_modifier_replacement = """\t\t\tmodifier = Modifier
\t\t\t\t.size(size)
\t\t\t\t.border(
\t\t\t\t\twidth = if (focused) 4.dp else 0.dp,
\t\t\t\t\tcolor = JellyfinTheme.colorScheme.buttonFocused,
\t\t\t\t\tshape = JellyfinTheme.shapes.medium,
\t\t\t\t)"""
    if card_modifier_anchor in cp and "width = if (focused) 4.dp else 0.dp" not in cp:
        cp = cp.replace(card_modifier_anchor, card_modifier_replacement, 1)

    focus_listener_anchor = """			_focused.value = view.isFocused
			composeView.onFocusChangeListener = { _, focused -> _focused.value = focused }"""
    focus_listener_nova = """			_focused.value = view.isFocused
			composeView.onFocusChangeListener = { focusedView, focused ->
				_focused.value = focused
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					focusedView.foreground = if (focused) {
						android.graphics.drawable.GradientDrawable().apply {
							setColor(android.graphics.Color.TRANSPARENT)
							setStroke(
								(3f * focusedView.resources.displayMetrics.density).toInt().coerceAtLeast(2),
								android.graphics.Color.rgb(229, 9, 20),
							)
							val rounding = android.util.TypedValue()
							cornerRadius = if (focusedView.context.theme.resolveAttribute(
								org.jellyfin.androidtv.R.attr.cardRounding,
								rounding,
								true,
							)) {
								when {
									rounding.type == android.util.TypedValue.TYPE_DIMENSION ->
										android.util.TypedValue.complexToDimension(
											rounding.data,
											focusedView.resources.displayMetrics,
										)
									rounding.resourceId != 0 ->
										focusedView.resources.getDimension(rounding.resourceId)
									else -> 6f * focusedView.resources.displayMetrics.density
								}
							} else {
								6f * focusedView.resources.displayMetrics.density
							}
						}
					} else {
						null
					}
				}
			}"""
    if focus_listener_anchor in cp:
        cp = cp.replace(focus_listener_anchor, focus_listener_nova, 1)

    card_presenter.write_text(cp, encoding="utf-8")


# ---------------------------------------------------------------------------
# NOVA preferred audio language
# ---------------------------------------------------------------------------
# Prefer English audio for both normal playback and hover previews. If a title
# has no English track, Jellyfin's normal/default audio selection is preserved.
playback_controller = root / "app/src/main/java/org/jellyfin/androidtv/ui/playback/PlaybackController.java"
if playback_controller.exists():
    pc = playback_controller.read_text(encoding="utf-8")

    # Each new movie/episode should start by preferring English rather than
    # inheriting a dub language chosen by track order.
    old_forced_audio = 'String forcedAudioLanguage = videoQueueManager.getValue().getLastPlayedAudioLanguageIsoCode();'
    if old_forced_audio not in pc:
        raise RuntimeError("Could not find PlaybackController forced audio language")
    pc = pc.replace(old_forced_audio, 'String forcedAudioLanguage = "eng";', 1)

    # Find the best English audio stream, preferring the normal dialogue track
    # over commentary/audio-description tracks where labels allow us to tell.
    best_guess_anchor = """    private Integer bestGuessAudioTrack(MediaSourceInfo info) {"""
    english_helper = """    private boolean isEnglishAudioTrack(MediaStream track) {
        if (track == null || track.getType() != MediaStreamType.AUDIO)
            return false;

        String language = track.getLanguage();
        if (language != null) {
            String normalized = language.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.equals("eng")
                    || normalized.equals("en")
                    || normalized.startsWith("en-")
                    || normalized.equals("english")) {
                return true;
            }
        }

        String title = track.getTitle();
        String displayTitle = track.getDisplayTitle();
        String label = ((title == null ? "" : title) + " " + (displayTitle == null ? "" : displayTitle))
                .toLowerCase(java.util.Locale.ROOT);
        return label.contains("english");
    }

    private boolean isCommentaryOrDescriptionTrack(MediaStream track) {
        String title = track.getTitle();
        String displayTitle = track.getDisplayTitle();
        String label = ((title == null ? "" : title) + " " + (displayTitle == null ? "" : displayTitle))
                .toLowerCase(java.util.Locale.ROOT);
        return label.contains("commentary")
                || label.contains("audio description")
                || label.contains("descriptive")
                || label.contains("director commentary")
                || label.contains("visually impaired");
    }

    private Integer preferredEnglishAudioTrack(MediaSourceInfo info) {
        if (info == null || info.getMediaStreams() == null)
            return null;

        Integer fallbackEnglish = null;
        for (MediaStream track : info.getMediaStreams()) {
            if (!isEnglishAudioTrack(track))
                continue;

            if (fallbackEnglish == null)
                fallbackEnglish = track.getIndex();

            if (!isCommentaryOrDescriptionTrack(track))
                return track.getIndex();
        }

        return fallbackEnglish;
    }

    private Integer bestGuessAudioTrack(MediaSourceInfo info) {"""
    if best_guess_anchor not in pc:
        raise RuntimeError("Could not find PlaybackController audio helper anchor")
    if "private Integer preferredEnglishAudioTrack" not in pc:
        pc = pc.replace(best_guess_anchor, english_helper, 1)

    # Tell Jellyfin which audio stream to use before it decides whether to
    # direct-play, remux or transcode.
    source_anchor = """        MediaSourceInfo currentMediaSource = getCurrentMediaSource();
        if (forcedAudioLanguage != null) {"""
    source_nova = """        MediaSourceInfo currentMediaSource = getCurrentMediaSource();
        if (internalOptions.getAudioStreamIndex() == null) {
            Integer preferredEnglishAudio = preferredEnglishAudioTrack(currentMediaSource);
            if (preferredEnglishAudio != null) {
                internalOptions.setAudioStreamIndex(preferredEnglishAudio);
            }
        }
        if (forcedAudioLanguage != null) {"""
    if source_anchor not in pc:
        raise RuntimeError("Could not find PlaybackController media source audio hook")
    pc = pc.replace(source_anchor, source_nova, 1)

    # Also make the post-playback-info default choose English before the server
    # default. Explicit in-player track changes still remain respected.
    default_audio_anchor = """        Integer lastChosenLanguage = lastChosenLanguageAudioTrack(info.getMediaSource());
        Integer remoteDefault = info.getMediaSource().getDefaultAudioStreamIndex();
        Integer bestGuess = bestGuessAudioTrack(info.getMediaSource());

        if (lastChosenLanguage != null)
            mDefaultAudioIndex = lastChosenLanguage;
        else if (remoteDefault != null)"""
    default_audio_nova = """        Integer preferredEnglish = preferredEnglishAudioTrack(info.getMediaSource());
        Integer lastChosenLanguage = lastChosenLanguageAudioTrack(info.getMediaSource());
        Integer remoteDefault = info.getMediaSource().getDefaultAudioStreamIndex();
        Integer bestGuess = bestGuessAudioTrack(info.getMediaSource());

        if (preferredEnglish != null)
            mDefaultAudioIndex = preferredEnglish;
        else if (lastChosenLanguage != null)
            mDefaultAudioIndex = lastChosenLanguage;
        else if (remoteDefault != null)"""
    if default_audio_anchor not in pc:
        raise RuntimeError("Could not find PlaybackController default audio selection")
    pc = pc.replace(default_audio_anchor, default_audio_nova, 1)

    playback_controller.write_text(pc, encoding="utf-8")


# Legacy/full-screen ExoPlayer should select an English muxed track before
# audible playback starts when direct-playing a file.
video_manager = root / "app/src/main/java/org/jellyfin/androidtv/ui/playback/VideoManager.java"
if video_manager.exists():
    vm = video_manager.read_text(encoding="utf-8")
    vm_audio_anchor = """.setAudioOffloadPreferences(new TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                        .build()
                )
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)"""
    vm_audio_nova = """.setAudioOffloadPreferences(new TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                        .build()
                )
                .setPreferredAudioLanguages("eng", "en", "en-GB", "en-US")
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)"""
    if vm_audio_anchor not in vm:
        raise RuntimeError("Could not find VideoManager track selector audio hook")
    vm = vm.replace(vm_audio_anchor, vm_audio_nova, 1)
    video_manager.write_text(vm, encoding="utf-8")


# Hover preview: request the English stream from Jellyfin so remux/transcode
# previews also use English, not merely direct-play files.
stream_resolver = root / "playback/jellyfin/src/main/kotlin/mediastream/JellyfinMediaStreamResolver.kt"
if stream_resolver.exists():
    sr = stream_resolver.read_text(encoding="utf-8")

    if "import org.jellyfin.sdk.model.api.MediaStream\n" not in sr:
        sr = sr.replace(
            "import org.jellyfin.sdk.model.api.MediaProtocol\n",
            "import org.jellyfin.sdk.model.api.MediaProtocol\nimport org.jellyfin.sdk.model.api.MediaStream\nimport org.jellyfin.sdk.model.api.MediaStreamType\n",
            1,
        )

    resolver_method_anchor = """	private suspend fun getPlaybackInfo(
		item: BaseItemDto,
		mediaSourceId: String? = null,
	): MediaInfo {"""
    resolver_helper = """	private fun MediaStream.isNovaEnglishAudio(): Boolean {
		if (type != MediaStreamType.AUDIO) return false
		val languageCode = language?.trim()?.lowercase().orEmpty()
		if (languageCode == "eng" || languageCode == "en" || languageCode.startsWith("en-") || languageCode == "english") {
			return true
		}
		val label = listOfNotNull(title, displayTitle).joinToString(" ").lowercase()
		return "english" in label
	}

	private fun MediaStream.isNovaCommentaryAudio(): Boolean {
		val label = listOfNotNull(title, displayTitle).joinToString(" ").lowercase()
		return "commentary" in label ||
			"audio description" in label ||
			"descriptive" in label ||
			"visually impaired" in label
	}

	private fun preferredEnglishAudioStreamIndex(
		item: BaseItemDto,
		mediaSourceId: String?,
	): Int? {
		val source = item.mediaSources.orEmpty()
			.firstOrNull { mediaSourceId == null || it.id == mediaSourceId }
			?: return null
		val english = source.mediaStreams.orEmpty().filter { it.isNovaEnglishAudio() }
		return english.firstOrNull { !it.isNovaCommentaryAudio() }?.index
			?: english.firstOrNull()?.index
	}

	private suspend fun getPlaybackInfo(
		item: BaseItemDto,
		mediaSourceId: String? = null,
	): MediaInfo {"""
    if resolver_method_anchor not in sr:
        raise RuntimeError("Could not find Jellyfin preview resolver hook")
    if "preferredEnglishAudioStreamIndex" not in sr:
        sr = sr.replace(resolver_method_anchor, resolver_helper, 1)

    playback_dto_anchor = """			data = PlaybackInfoDto(
				mediaSourceId = mediaSourceId,
				deviceProfile = profile,"""
    playback_dto_nova = """			data = PlaybackInfoDto(
				mediaSourceId = mediaSourceId,
				audioStreamIndex = preferredEnglishAudioStreamIndex(item, mediaSourceId),
				deviceProfile = profile,"""
    if playback_dto_anchor not in sr:
        raise RuntimeError("Could not find preview PlaybackInfoDto hook")
    sr = sr.replace(playback_dto_anchor, playback_dto_nova, 1)

    stream_resolver.write_text(sr, encoding="utf-8")


# Hover preview direct-play uses the rewrite ExoPlayer backend, so set the same
# language preference at track-selection time too.
exo_backend = root / "playback/media3/exoplayer/src/main/kotlin/ExoPlayerBackend.kt"
if exo_backend.exists():
    eb = exo_backend.read_text(encoding="utf-8")
    exo_audio_anchor = """					setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)"""
    exo_audio_nova = """					setPreferredAudioLanguages("eng", "en", "en-GB", "en-US")
					setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)"""
    if exo_audio_anchor not in eb:
        raise RuntimeError("Could not find preview ExoPlayer audio preference hook")
    eb = eb.replace(exo_audio_anchor, exo_audio_nova, 1)
    exo_backend.write_text(eb, encoding="utf-8")


# Disable verbose Timber logging in production builds. Debug builds keep the
# original diagnostics for development, while NOVA v1 release avoids log spam.
log_initializer = root / "app/src/main/java/org/jellyfin/androidtv/LogInitializer.kt"
if log_initializer.exists():
    li = log_initializer.read_text(encoding="utf-8")
    old_log_block = """		// Initialize the logging library
		Timber.plant(Timber.DebugTree())
		Timber.i("Debug tree planted")"""
    new_log_block = """		// Keep verbose logcat output for development only.
		if (BuildConfig.DEBUG) {
			Timber.plant(Timber.DebugTree())
			Timber.i("Debug tree planted")
		}"""
    if old_log_block in li:
        li = li.replace(old_log_block, new_log_block, 1)
    log_initializer.write_text(li, encoding="utf-8")


print("Applied NOVA branding, Home UI, cinematic library pages, direct playback and global red focus cursor.")
