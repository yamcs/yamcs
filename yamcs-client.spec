Name: 		yamcs-client
Version: 	$VERSION$+r$REVISION$
Release: 	1

Group:		MCS
Summary: 	Yamcs Client Utilities

Vendor:		Space Applications Services
Packager:	Yamcs Team <yamcs@spaceapplications.com>
License: 	AGPL (client) + LGPL (API)
URL: 		https://www.yamcs.org
Prefix: 	/opt/yamcs-client
BuildArch:	noarch

%description
Client utilities for Yamcs.

%clean
rm -rf %{buildroot}

%install
cd %{name}-%{version}
mkdir -p %{buildroot}/%{prefix}/mdb

cp -a yamcs-client/etc %{buildroot}/%{prefix}/
cp -a yamcs-client/bin %{buildroot}/%{prefix}/
cp -a yamcs-client/lib %{buildroot}/%{prefix}/
cp yamcs-client/target/yamcs*.jar %{buildroot}/%{prefix}/lib

mv %{buildroot}/%{prefix}/etc/yamcs-ui.yaml.sample %{buildroot}/%{prefix}/etc/yamcs-ui.yaml
mv %{buildroot}/%{prefix}/etc/event-viewer.yaml.sample %{buildroot}/%{prefix}/etc/event-viewer.yaml

# Clean-up
rm %{buildroot}/%{prefix}/bin/*.bat
rm %{buildroot}/%{prefix}/lib/yamcs-*-sources.jar

%files
%defattr(-,root,root)

%dir %{prefix}
%config %{prefix}/mdb
%config %{prefix}/etc
%{prefix}/lib

%dir %{prefix}/bin
%attr(755, root, root) %{prefix}/bin/*
