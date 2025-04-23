# yamcs-nos3
YAMCS for NOS3

This example lets you test TM decryption and TC encryption with the NOS3 simulator in a virtual machine: https://github.com/nasa/nos3/

Tests were done with the commit `1e34337bdf8aa6bfb5adb4f68974be4e8de0eee0` (the latest `dev` commit at the time of testing).

The key used for encryption/decryption in Yamcs has been extracted from NOS3 source code.

## Setup
Check out the correct revision in NOS3, as specified above, along with its submodules.

Apply the [patch](./nos3-yamcs.patch) to prepare NOS3 for integration with the new Yamcs version.
The patch fixes some errors in the code that prevented compilation, and updates configuration.

Start the NOS3 virtual machine following their instructions.
In the virtualbox machine settings, allow forwarding UDP ports 8010 and 8011.

In `cfg/sims/nos3-simulator.xml`, update `simulator/generic_radio_sim/hardware-model/connection/gsw` IP to docker bridge IP of the machine in virtualbox (in tests, it was 172.17.0.1).

Build and launch NOS3.

Connect the radio simulator to the bridge network:

```sh
docker network connect bridge sc_1_radio_sim
```

In both the virtualbox machine and the docker container `sc_1_radio_sim`, install the packages: socat, iproute2.


To forward telemetry to Yamcs outside of virtualbox:
- in the virtualbox guest machine, run `socat udp-listen:8011,bind=172.17.0.1,reuseaddr,fork udp:10.0.2.2:8011` (IP address may differ -- 172.17.0.1 is the docker bridge IP of the virtualbox machine, 10.0.2.2 is the IP of the host machine outside of virtualbox)
- the data flow is: data from sc_1_radio_sim goes to port 8011 on docker host IP, socat sends that to the virtualbox host IP, Yamcs on the outside host gets the data.


To forward telecommands from Yamcs outside of virtualbox to the flight software:
- in the virtualbox guest, forward to docker: `socat udp-listen:8010,reuseaddr,fork udp:172.17.0.2:8010` (172.17.0.2 is the docker bridge IP of sc_1_radio_sim)
- in sc_1_radio_sim, forward to correct port: `socat udp-listen:8010,bind=172.17.0.2,reuseaddr,fork udp:172.19.0.21:8010` (first is bridge IP of sc_1_radio_sim, second is nos3 network IP of sc_1_radio_sim)
- the data flow is: data from Yamcs goes to port 8010 of virtualbox guest. Socat in virtualbox guest forwards it to port 8010 of bridge interface of sc_1_radio_sim. Socat in sc_1_radio_sim forwards it from the bridge interface to the nos3 network interface. Flight software receives it via the nos3 network.

## Tests
To test telemetry:
1. In the Yamcs instance outside of the virtual machine, switch to the links view, and observe radio-in has no activity
2. In the Yamcs instance in the virtual machine, run the command `/CFS/CMD/TO_ENABLE_OUTPUT`
3. In the Yamcs instance outside of the virtual machine, observe that the radio-in link has activity. See that there are no errors in the Yamcs logs.

To test telecommands:
1. In the Yamcs instance in the virtual machine, import the NOS3 sample display, and open it.
2. On the sample display, observe that the counters do not change, and that the field says DISABLED.
3. In the Yamcs instance outside of the virtual machine, run the command `/SAMPLE/CMD/SAMPLE_ENABLE_CC`
4. In the Yamcs instance in the virtual machine, observe that the counters are now increasing and that the field says ENABLED.
