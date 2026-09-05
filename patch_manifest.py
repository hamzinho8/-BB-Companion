with open('app/src/main/AndroidManifest.xml', 'r') as f:
    content = f.read()

if "android.permission.RECEIVE_BOOT_COMPLETED" not in content:
    content = content.replace("<uses-permission android:name=\"android.permission.INTERNET\" />", "<uses-permission android:name=\"android.permission.INTERNET\" />\n    <uses-permission android:name=\"android.permission.RECEIVE_BOOT_COMPLETED\" />")

if "BootReceiver" not in content:
    receiver_xml = """        <receiver
            android:name="com.hamza.blackberrybridge.receivers.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>"""
    content = content.replace("</application>", receiver_xml + "\n    </application>")

with open('app/src/main/AndroidManifest.xml', 'w') as f:
    f.write(content)
