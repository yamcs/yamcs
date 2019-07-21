Name: yamcs-simulation
Version: @@VERSION@@
Release: @@RELEASE@@

Group: MCS
Summary: Example of how Yamcs can be configured, with simulated data.

Vendor: Space Applications Services
Packager: Yamcs Team <yamcs@spaceapplications.com>
License: AGPL
URL: https://www.yamcs.org
Prefix: /opt/yamcs
BuildArch: noarch
Requires: yamcs


%description
Configures Yamcs for simulation


%install
cd %{name}-%{version}-%{release}

mkdir -p %{buildroot}/%{prefix}/lib
cp simulation/target/simulation*.jar %{buildroot}/%{prefix}/lib

cp -r simulation/bin %{buildroot}/%{prefix}
cp -r simulation/mdb %{buildroot}/%{prefix}

cp -r simulation/etc %{buildroot}/%{prefix}
mv %{buildroot}/%{prefix}/etc/logging.properties.rpm %{buildroot}/%{prefix}/etc/logging.properties

mkdir -p %{buildroot}/storage/yamcs-data
mkdir -p %{buildroot}/storage/yamcs-incoming

# Clean-up
rm %{buildroot}/%{prefix}/bin/*.bat
rm %{buildroot}/%{prefix}/lib/*-sources.jar
rm %{buildroot}/%{prefix}/etc/users.yaml
rm %{buildroot}/%{prefix}/etc/roles.yaml


%files
%defattr(-,root,root)

%attr(755, root, root) %{prefix}/bin/*
%{prefix}/lib/*

%config %{prefix}/mdb/*
%config %{prefix}/etc/*

%dir %attr(700,yamcs,yamcs) /storage/yamcs-data
%dir %attr(700,yamcs,yamcs) /storage/yamcs-incoming
