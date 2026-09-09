with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BBBluetoothManager.kt', 'r') as f:
    content = f.read()

# Fix the broken declaration area
broken_code = """    private var outWriter: PrintWriter? = null
    private var isIntentionalDisconnect = false
        reconnectJob?.cancel()
        lastDevice = device
        lastService = service
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastDevice: BluetoothDevice? = null
    private var lastService: BluetoothService? = null"""

fixed_code = """    private var outWriter: PrintWriter? = null
    private var isIntentionalDisconnect = false
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastDevice: BluetoothDevice? = null
    private var lastService: BluetoothService? = null"""

content = content.replace(broken_code, fixed_code)

# Now put it in connectToDevice properly where it belongs
target_connect = """    fun connectToDevice(device: BluetoothDevice, service: BluetoothService) {
        stopScanning()
        connectionJob?.cancel()
        isIntentionalDisconnect = false"""

fixed_connect = """    fun connectToDevice(device: BluetoothDevice, service: BluetoothService) {
        stopScanning()
        connectionJob?.cancel()
        isIntentionalDisconnect = false
        reconnectJob?.cancel()
        lastDevice = device
        lastService = service"""
        
content = content.replace(target_connect, fixed_connect)

with open('app/src/main/java/com/hamza/blackberrybridge/bluetooth/BBBluetoothManager.kt', 'w') as f:
    f.write(content)
