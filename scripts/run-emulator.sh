#!/usr/bin/env bash
set -eu
SDK="${ANDROID_HOME:?Set ANDROID_HOME}"
export ANDROID_I_WANT_MY_TCG=yes
export ANDROID_SDK_ROOT="$SDK" ANDROID_AVD_HOME="$PWD/build/test-avds" ANDROID_ADB_SERVER_PORT=5042
export PATH="$SDK/platform-tools:$PATH"
AVD="$ANDROID_AVD_HOME/english33.avd"
mkdir -p "$AVD" qa
if [ ! -s "$AVD/userdata-qemu.img" ]; then
  command -v mkfs.ext4 >/dev/null || { printf 'Install e2fsprogs to initialize the test emulator.\n' >&2; exit 1; }
  truncate -s 1024M "$AVD/userdata-qemu.img"
  mkfs.ext4 -F -q "$AVD/userdata-qemu.img"
fi
printf 'avd.ini.encoding=UTF-8\npath=%s\ntarget=android-33\n' "$AVD" > "$ANDROID_AVD_HOME/english33.ini"
cat > "$AVD/config.ini" <<EOF
AvdId=english33
avd.ini.displayname=English Android 13
abi.type=x86_64
image.sysdir.1=system-images/android-33/default/x86_64/
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=360
hw.lcd.height=780
hw.lcd.density=160
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
disk.dataPartition.size=1024M
showDeviceFrame=no
fastboot.forceColdBoot=yes
EOF
"$SDK/platform-tools/adb" start-server
"$SDK/emulator/emulator" -avd english33 -no-metrics -no-window -no-snapshot -no-boot-anim -no-audio -accel off -gpu swiftshader_indirect -port 5580 -data "$AVD/userdata-qemu.img" -partition-size 1024 -prop ro.hw_timeout_multiplier=10 -show-kernel > qa/emulator.log 2>&1 &
emu_pid=$!
trap 'kill "$emu_pid" 2>/dev/null || true' EXIT
# Software acceleration can exceed AOSP's hardware watchdog deadlines during boot.
# This is confined to the disposable, root-capable emulator on port 5580.
(
  adb -s emulator-5580 wait-for-device
  adb -s emulator-5580 root
  adb -s emulator-5580 wait-for-device
  adb -s emulator-5580 shell 'if [ "$(getprop ro.hw_timeout_multiplier)" != 10 ]; then setprop ro.hw_timeout_multiplier 10; setprop ctl.restart zygote; fi'
) > qa/emulator-watchdog-setup.log 2>&1 &
printf 'EMULATOR_STARTED\n'
while IFS= read -r line; do eval "$line" || true; done
