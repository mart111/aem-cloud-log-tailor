
# AEMaaCS Log Tailor

The Java way to tail the logs on AEMaaCS.


## Installation

Make sure you have JDK 11 installed in your environment.
To keep it easy you can put the ".properties" file, "private.key" file and the jar in the same directory.
## Usage

```
java -jar /path/to/aem-cloud-logging.jar -f <path_to_properties_file> -e <env_id_adobe> -s <service_name> -log <log_file_name> -p <program_id>
```


## Example of ".properties file"

```
orgId=<orgId_value>
technicalAccountId=<technicalAccountId_value>
clientId=<clientId_value> # AKA API Key
clientSecret=<clientSecret_value>
privateKeyPath=<privateKeyPath> # path/to/private.key file
```


## Additional Info
The values for ".properties" file can be taken from [here](https://developer.adobe.com/console/projects).
If you don't have already created project, you have to create one, but I'm pretty sure you have 🙂
