EVO-Service
This is Java based application with a windows service wrapper to act as a bridge between the 4D ePOS system and an Ingenico Lane/3000 card terminal.

All development has been done in IntelliJ, using Java JDK 1.8.0_281 (consistent with the library provided).

Required Libraries:
* Gson - v2.10.1 - Java library that can be used to convert Java Objects into their JSON representation https://github.com/google/gson
* ecrli.jar - v14.1.0 - EVO Payments SDK

These libraries have been included in the lib/ folder, and should be correctly setup within the IntelliJ project already.

Config
A config.json file is required in the root directory of the repository. An example of its contents can be found below:

{
  "terminalIp": "xxx.xxx.xxx.xxx",
  "terminalPort": 3000,
  "terminalTimeout": 10000,
  "serverPort": 4000,
  "serverTimeout": 10000
}

Build
To build the EVOPay-Service.jar file from IntelliJ, simply go to Build/Build Artifacts and click the Build action. This will build EVO-Service.jar and put it in the build/ directory.

Once the EVOPay-Service.jar file has been built, the build directory is ready to be placed on an EPOS system and the service restarted.

