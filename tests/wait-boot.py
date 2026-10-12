"""Wait for the disposable emulator; tolerate adb restarts during its slow software boot."""
import subprocess,time
end=time.monotonic()+1200
attempt=0
while time.monotonic()<end:
    try:
        result=subprocess.run(['adb','-s','emulator-5580','shell','getprop','sys.boot_completed'],capture_output=True,timeout=60)
        if result.stdout.strip()==b'1':
            print('Android boot completed',flush=True);break
    except subprocess.TimeoutExpired:
        pass
    if attempt%15==0:print('Waiting for Android boot',flush=True)
    attempt+=1
    time.sleep(2)
else:raise RuntimeError('Android emulator boot timed out')
