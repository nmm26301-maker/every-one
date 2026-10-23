#!/usr/bin/env bash
set -euo pipefail
output="${1:?Pass a disposable output directory}"
mkdir -p "$output"
cp "$(dirname "$0")"/media/* "$output/"
ffmpeg -y -hide_banner -loglevel error -f lavfi -i color=c=navy:s=320x180:r=15 \
  -f lavfi -i sine=frequency=440:sample_rate=16000 -t 20 -c:v libx264 \
  -pix_fmt yuv420p -c:a aac -movflags +faststart "$output/video.mp4"
ffmpeg -y -hide_banner -loglevel error -f lavfi \
  -i 'aevalsrc=0|0.45*sin(2*PI*170*t):s=48000:d=1' -c:a aac "$output/right-channel.m4a"
