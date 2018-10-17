Banner Rigde BLE FW
===================

Please follow the guideline below for upgrading BLE FW version: 2.0.6

Programming sequence:

$ nrfjprog --family nrf52 --eraseall
$ nrfjprog --family nrf52 --program <hex file> --reset
