Name: packet-viewer
Version: @@VERSION@@
Release: @@RELEASE@@

Group: Unspecified
Summary: Yamcs Packet Viewer

Vendor: Space Applications Services
Packager: Yamcs Team <yamcs@spaceapplications.com>
License: AGPL (client) + LGPL (API)
URL: https://yamcs.org
BuildArch: noarch


%description
Packet Viewer for Yamcs.


%install
cd %{name}-%{version}-%{release}

mkdir -p %{buildroot}
cp -r opt %{buildroot}

mkdir -p %{buildroot}%{_bindir}
ln -fs /opt/packet-viewer/bin/packet-viewer.sh %{buildroot}%{_bindir}/packet-viewer
rm %{buildroot}/opt/packet-viewer/bin/*.bat


%files
%defattr(-,root,root)

%dir /opt/packet-viewer
%config /opt/packet-viewer/mdb
%config /opt/packet-viewer/etc
/opt/packet-viewer/lib
/opt/packet-viewer/tm-data

%dir /opt/packet-viewer/bin
%attr(755, root, root) /opt/packet-viewer/bin/*
%{_bindir}/packet-viewer
