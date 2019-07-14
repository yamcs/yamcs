Name: packet-viewer
Version: @@VERSION@@
Release: @@RELEASE@@

Group: MCS
Summary: Yamcs Packet Viewer

Vendor: Space Applications Services
Packager: Yamcs Team <yamcs@spaceapplications.com>
License: AGPL (client) + LGPL (API)
URL: https://www.yamcs.org
Prefix: /opt/packet-viewer
BuildArch: noarch


%description
Packet Viewer for Yamcs.


%install
cd %{name}-%{version}-%{release}

mkdir -p %{buildroot}
cp -r opt %{buildroot}

rm %{buildroot}/%{prefix}/bin/*.bat


%files
%defattr(-,root,root)

%dir %{prefix}
%config %{prefix}/mdb
%config %{prefix}/etc
%{prefix}/lib

%dir %{prefix}/bin
%attr(755, root, root) %{prefix}/bin/*
