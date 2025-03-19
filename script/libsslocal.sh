#!/bin/bash
set -e
#mount -o remount,size=8G,noatime /sys/devices/virtual/dmi/id
#mount -o remount,size=8G,noatime /sys/fs/cgroup
df -h
source bash/ndk.sh
#bash jobs/rust.sh nightly-2022-09-22
bash bash/rust.sh nightly
source $HOME/.cargo/env
export PATH=$PATH:$HOME/.cargo/bin
apt-get -qqy update
apt-get --quiet install --yes git curl wget ca-certificates python2.7-minimal python3
apt-get -qqy autoremove --purge
apt-get -qqy clean
