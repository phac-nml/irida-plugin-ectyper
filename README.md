# IRIDA ecTyper Plugin for E.coli serotyping


# Building/Packaging

Building and packaging this code is accomplished using [Apache Maven][maven]. However, you will first need to install [IRIDA][] to your local Maven repository. The version of IRIDA you install will have to correspond to the version found in the `irida.version.compiletime` property in the [pom.xml][] file of this project. Right now, this is IRIDA version `19.01.3`.

## Installing IRIDA to local Maven repository

To install IRIDA to your local Maven repository please do the following:

1. Clone the IRIDA project

```bash
git clone https://github.com/phac-nml/irida.git
cd irida
```

2. Checkout appropriate version of IRIDA

```bash
git checkout 19.01.3
```

3. Install IRIDA to local repository

```bash
mvn clean install -DskipTests
```

## Building the plugin

Once you've installed IRIDA as a dependency, you can proceed to building this plugin. Please run the following commands:

```bash
cd irida-plugin-example

mvn clean package
```

Once complete, you should end up with a file `target/ectyper-plugin-1.0-SNAPSHOT.jar` which can be installed as a plugin to IRIDA.

If you have previously [setup IRIDA][irida-setup] before you may copy this JAR file to `/etc/irida/plugins` and restart IRIDA.  The plugin should now show up in the **Analyses > Pipelines** section of IRIDA.


# Dependencies

The following dependencies are required in order to make use of this plugin.

* [IRIDA][] >= 0.23.0
* [Java][] >= 1.8 and [Maven][maven] (for building)


