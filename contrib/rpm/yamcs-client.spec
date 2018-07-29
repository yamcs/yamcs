Name: yamcs-client
Version: $VERSION$+r$REVISION$
Release: 1

Group: MCS
Summary: Yamcs Client Utilities

Vendor: Space Applications Services
Packager: Yamcs Team <yamcs@spaceapplications.com>
License: AGPL (client) + LGPL (API)
URL: https://www.yamcs.org
Prefix: /opt/yamcs-client
BuildArch: noarch

%description
Client utilities for Yamcs.

%install
cd %{name}-%{version}

mkdir -p %{buildroot}/%{prefix}
cp -a * %{buildroot}/%{prefix}/

rm %{buildroot}/%{prefix}/bin/*.bat

%files
%defattr(-,root,root)

%dir %{prefix}
%config %{prefix}/mdb
%config %{prefix}/etc
%{prefix}/lib

%dir %{prefix}/bin
%attr(755, root, root) %{prefix}/bin/*
