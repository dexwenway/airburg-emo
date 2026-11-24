"""扫描附近的 BLE 设备，打印广播信息。用于确认 EMO 的广播名与地址。"""

import asyncio

from bleak import BleakScanner

DURATION = 12.0


async def main() -> None:
    seen = {}

    def on_detect(device, adv):
        key = device.address
        entry = seen.setdefault(key, {"name": device.name or adv.local_name, "rssi": [], "uuids": set(), "mfr": {}})
        entry["rssi"].append(adv.rssi)
        entry["uuids"].update(adv.service_uuids or [])
        for company_id, payload in (adv.manufacturer_data or {}).items():
            entry["mfr"][company_id] = payload.hex()

    scanner = BleakScanner(detection_callback=on_detect)
    await scanner.start()
    await asyncio.sleep(DURATION)
    await scanner.stop()

    print(f"{len(seen)} devices in {DURATION:.0f}s\n")
    for addr, info in sorted(seen.items(), key=lambda kv: kv[1]["name"] or ""):
        rssi = max(info["rssi"]) if info["rssi"] else "?"
        name = info["name"] or "(no name)"
        print(f"{name:28} {addr}  rssi={rssi}")
        if info["uuids"]:
            print(f"    services: {', '.join(sorted(info['uuids']))}")
        if info["mfr"]:
            print(f"    manufacturer: {info['mfr']}")


if __name__ == "__main__":
    asyncio.run(main())
