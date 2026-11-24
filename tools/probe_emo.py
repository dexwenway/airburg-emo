"""连接 EMO 并 dump GATT、电池与数据帧，用于校准一代设备的协议字段。

用法:
    python probe_emo.py                 # 自动扫描名字含 PM2.5 或 EMO 的设备
    python probe_emo.py <address>       # 指定地址
"""

import asyncio
import sys
import time

from bleak import BleakClient, BleakScanner

NAME_HINTS = ("PM2.5", "PM25", "EMO")
BATTERY_CHAR = "00002a19-0000-1000-8000-00805f9b34fb"
VENDOR_SERVICE = "0000fff0-0000-1000-8000-00805f9b34fb"
DATA_CHAR = "0000fff2-0000-1000-8000-00805f9b34fb"
CMD_CHAR = "0000fff3-0000-1000-8000-00805f9b34fb"

WARMUP_SECONDS = 22
SAMPLE_SECONDS = 60
SAMPLE_INTERVAL = 2.0


def parse_candidates(data: bytes) -> str:
    if len(data) < 14:
        return "frame too short"
    be = int.from_bytes(data[10:12], "big") * 1.265
    le = int.from_bytes(data[10:12], "little") * 1.265
    p_be = int.from_bytes(data[12:14], "big") * 2.56
    p_le = int.from_bytes(data[12:14], "little") * 2.56
    return (
        f"pm2.5(be)={be:7.1f} pm2.5(le)={le:7.1f} "
        f"particles(be)={p_be:8.0f} particles(le)={p_le:8.0f}"
    )


async def find_device() -> str | None:
    print("scanning for EMO ...")
    devices = await BleakScanner.discover(timeout=10.0)
    for dev in devices:
        name = (dev.name or "").upper()
        if any(hint in name for hint in NAME_HINTS):
            print(f"found {dev.name!r} [{dev.address}]")
            return dev.address
    print("no EMO-like device found; devices seen:")
    for dev in devices:
        if dev.name:
            print(f"  {dev.name!r} [{dev.address}]")
    return None


async def dump_gatt(client: BleakClient) -> None:
    print("\n=== services ===")
    for service in client.services:
        print(f"[{service.uuid}] {service.description or ''}")
        for char in service.characteristics:
            print(f"  char {char.uuid}  props={','.join(char.properties)}")
        for char in service.characteristics:
            if "read" in char.properties:
                try:
                    value = await client.read_gatt_char(char.uuid)
                    print(f"    read {char.uuid} -> {value.hex()}")
                except Exception as exc:
                    print(f"    read {char.uuid} failed: {exc}")


async def read_safely(client: BleakClient, uuid: str) -> bytes | None:
    try:
        return await client.read_gatt_char(uuid)
    except Exception as exc:
        print(f"read {uuid} failed: {exc}")
        return None


async def probe(address: str) -> None:
    async with BleakClient(address) as client:
        print(f"connected={client.is_connected}")

        battery = await read_safely(client, BATTERY_CHAR)
        if battery:
            print(f"battery={battery[0]}%")

        await dump_gatt(client)
        print("\n=== characteristics of interest ===")
        for uuid in (DATA_CHAR, CMD_CHAR):
            await read_safely(client, uuid)

        print(f"\nwriting cmd char {CMD_CHAR} with 0x01")
        try:
            await client.write_gatt_char(CMD_CHAR, b"\x01", response=False)
        except Exception as exc:
            print(f"write failed: {exc}")

        print(f"warm-up {WARMUP_SECONDS}s ...")
        await asyncio.sleep(WARMUP_SECONDS)

        print("\n=== sampling ===")
        deadline = time.monotonic() + SAMPLE_SECONDS
        while time.monotonic() < deadline:
            data = await read_safely(client, DATA_CHAR)
            stamp = time.strftime("%H:%M:%S")
            if data:
                print(f"[{stamp}] {data.hex()}\n         {parse_candidates(data)}")
            await asyncio.sleep(SAMPLE_INTERVAL)


async def main() -> None:
    address = sys.argv[1] if len(sys.argv) > 1 else await find_device()
    if not address:
        return
    await probe(address)


if __name__ == "__main__":
    asyncio.run(main())
