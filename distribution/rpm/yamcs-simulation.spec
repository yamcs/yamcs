Name: yamcs-simulation
Version: @@VERSION@@
Release: @@RELEASE@@

Group: Unspecified
Summary: Example of how Yamcs can be configured, with simulated data.

Vendor: Space Applications Services
Packager: Yamcs Team <yamcs@spaceapplications.com>
License: AGPL
URL: https://yamcs.org
Prefix: /opt/yamcs
BuildArch: noarch
Requires: yamcs


%description
Configures Yamcs for simulation


%install
cd %{name}-%{version}-%{release}
cd examples/simulation

mkdir -p %{buildroot}/%{prefix}/lib
cp target/simulation*.jar %{buildroot}/%{prefix}/lib

cp -r src/main/yamcs/mdb %{buildroot}/%{prefix}

cp -r src/main/yamcs/etc %{buildroot}/%{prefix}
cp -r src/main/yamcs/etc-rpm/* %{buildroot}/%{prefix}/etc

mkdir -p %{buildroot}/%{prefix}/displays
mkdir -p %{buildroot}/%{prefix}/stacks
mkdir -p %{buildroot}/storage/yamcs-data
mkdir -p %{buildroot}/storage/yamcs-incoming

# Clean-up
rm %{buildroot}/%{prefix}/lib/*-sources.jar


%files
%defattr(-,root,root)

%{prefix}/lib/*

%config %{prefix}/mdb/*
%config %{prefix}/etc/*

%dir %attr(700,yamcs,yamcs) %{prefix}/displays
%dir %attr(700,yamcs,yamcs) %{prefix}/stacks
%dir %attr(700,yamcs,yamcs) /storage/yamcs-data
%dir %attr(700,yamcs,yamcs) /storage/yamcs-incoming
