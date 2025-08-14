import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { GoogleMapsModule } from '@angular/google-maps';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { interval, Subject, takeUntil } from 'rxjs';
import { MapsConfigService } from '../../shared/services/maps-config.service';

interface SatellitePosition {
  latitude: number;
  longitude: number;
  altitude: number;
  timestamp: Date;
}

interface OrbitalPath {
  positions: google.maps.LatLngLiteral[];
}

@Component({
  selector: 'app-satellite-map',
  templateUrl: './satellite-map.component.html',
  styleUrl: './satellite-map.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [GoogleMapsModule, MatProgressSpinnerModule, WebappSdkModule],
})
export class SatelliteMapComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private orbitalPathLoaded = false;
  private simulationTime = 0;
  private previousPosition: SatellitePosition | null = null;

  // Template properties - simple properties, no reactive streams
  showLoading = true;
  showMap = false;
  showMapError = false;
  currentPosition: SatellitePosition | null = null;
  markerPosition: google.maps.LatLngLiteral | null = null;
  pathPositions: google.maps.LatLngLiteral[] = [];

  // Map configuration - Static after initialization
  mapOptions: any = {};

  // Marker options
  markerOptions: any = {};

  // Polyline options for orbital path
  polylineOptions: any = {};

  constructor(
    private mapsConfigService: MapsConfigService,
    private cdr: ChangeDetectorRef
  ) {}

  async ngOnInit() {
    try {
      // Initialize Google Maps before loading satellite data
      await this.mapsConfigService.initializeGoogleMaps();
      
      // Set up Google Maps configurations after API is loaded
      this.setupMapOptions();
      this.setupMarkerOptions();
      this.setupPolylineOptions();
      
      this.showMap = true;
      this.showLoading = false; // Hide loading after maps are ready
      this.cdr.detectChanges(); // Trigger change detection
      
      this.loadSatelliteData();
      
      // Update satellite position every 1 second
      interval(1000)
        .pipe(takeUntil(this.destroy$))
        .subscribe(() => {
          this.loadSatelliteData();
        });
    } catch (error) {
      console.error('Failed to initialize Google Maps:', error);
      this.showLoading = false;
      this.showMap = false;
      this.showMapError = true;
      
      // Still load satellite data for overlays even without map
      this.loadSatelliteData();
      
      // Update satellite position every 1 second even without map
      interval(1000)
        .pipe(takeUntil(this.destroy$))
        .subscribe(() => {
          this.loadSatelliteData();
        });
    }
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private setupMapOptions() {
    this.mapOptions = {
      center: { lat: 0, lng: 0 },
      zoom: 2,
      styles: [
        // Light theme styles
        { elementType: 'geometry', stylers: [{ color: '#f5f5f5' }] },
        { elementType: 'labels.icon', stylers: [{ visibility: 'off' }] },
        { elementType: 'labels.text.fill', stylers: [{ color: '#616161' }] },
        { elementType: 'labels.text.stroke', stylers: [{ color: '#f5f5f5' }] },
        {
          featureType: 'administrative',
          elementType: 'geometry',
          stylers: [{ color: '#fefefe' }],
        },
        {
          featureType: 'administrative.country',
          elementType: 'labels.text.fill',
          stylers: [{ color: '#424242' }],
        },
        {
          featureType: 'administrative.land_parcel',
          stylers: [{ visibility: 'off' }],
        },
        {
          featureType: 'administrative.locality',
          elementType: 'labels.text.fill',
          stylers: [{ color: '#757575' }],
        },
        {
          featureType: 'poi',
          elementType: 'labels.text.fill',
          stylers: [{ color: '#757575' }],
        },
        {
          featureType: 'poi.park',
          elementType: 'geometry',
          stylers: [{ color: '#e5e5e5' }],
        },
        {
          featureType: 'poi.park',
          elementType: 'labels.text.fill',
          stylers: [{ color: '#9e9e9e' }],
        },
        {
          featureType: 'poi.park',
          elementType: 'labels.text.stroke',
          stylers: [{ color: '#ffffff' }],
        },
        {
          featureType: 'road',
          elementType: 'geometry.fill',
          stylers: [{ color: '#ffffff' }],
        },
        {
          featureType: 'road',
          elementType: 'labels.text.fill',
          stylers: [{ color: '#757575' }],
        },
        {
          featureType: 'road.arterial',
          elementType: 'geometry',
          stylers: [{ color: '#ffffff' }],
        },
        {
          featureType: 'road.highway',
          elementType: 'geometry',
          stylers: [{ color: '#dadada' }],
        },
        {
          featureType: 'road.highway.controlled_access',
          elementType: 'geometry',
          stylers: [{ color: '#e9e9e9' }],
        },
        {
          featureType: 'road.local',
          elementType: 'labels.text.fill',
          stylers: [{ color: '#9e9e9e' }],
        },
        {
          featureType: 'transit',
          elementType: 'labels.text.fill',
          stylers: [{ color: '#757575' }],
        },
        {
          featureType: 'water',
          elementType: 'geometry',
          stylers: [{ color: '#c9c9c9' }],
        },
        {
          featureType: 'water',
          elementType: 'labels.text.fill',
          stylers: [{ color: '#9e9e9e' }],
        },
      ],
      mapTypeControl: false,
      streetViewControl: false,
      fullscreenControl: true,
      fullscreenControlOptions: {
        position: google.maps.ControlPosition.BOTTOM_RIGHT,
      },
    };
  }

  private setupMarkerOptions() {
    this.markerOptions = {
      icon: {
        // Stroke-only satellite design for outline appearance
        path: 'M32,80 L88,80 Q100,80 100,92 L100,308 Q100,320 88,320 L32,320 Q20,320 20,308 L20,92 Q20,80 32,80 Z M40,80 L40,320 M60,80 L60,320 M80,80 L80,320 M20,100 L100,100 M20,120 L100,120 M20,140 L100,140 M20,160 L100,160 M20,180 L100,180 M20,200 L100,200 M20,220 L100,220 M20,240 L100,240 M20,260 L100,260 M20,280 L100,280 M20,300 L100,300 M312,80 L368,80 Q380,80 380,92 L380,308 Q380,320 368,320 L312,320 Q300,320 300,308 L300,92 Q300,80 312,80 Z M320,80 L320,320 M340,80 L340,320 M360,80 L360,320 M300,100 L380,100 M300,120 L380,120 M300,140 L380,140 M300,160 L380,160 M300,180 L380,180 M300,200 L380,200 M300,220 L380,220 M300,240 L380,240 M300,260 L380,260 M300,280 L380,280 M300,300 L380,300 M108,170 L142,170 Q150,170 150,178 L150,222 Q150,230 142,230 L108,230 Q100,230 100,222 L100,178 Q100,170 108,170 Z M258,170 L292,170 Q300,170 300,178 L300,222 Q300,230 292,230 L258,230 Q250,230 250,222 L250,178 Q250,170 258,170 Z M162,150 L238,150 Q250,150 250,162 L250,238 Q250,250 238,250 L162,250 Q150,250 150,238 L150,162 Q150,150 162,150 Z M175,200 A25,25 0 1,1 225,200 A25,25 0 1,1 175,200 M194,200 A6,6 0 1,1 206,200 A6,6 0 1,1 194,200 Z M222,220 L228,220 Q230,220 230,222 L230,226 Q230,228 228,228 L222,228 Q220,228 220,226 L220,222 Q220,220 222,220 Z M172,180 L180,180 Q182,180 182,182 L182,186 Q182,188 180,188 L172,188 Q170,188 170,186 L170,182 Q170,180 172,180 Z',
        fillOpacity: 0,
        strokeColor: '#ff4444',
        strokeWeight: 1,
        scale: 0.1,
        anchor: { x: 200, y: 200 },
        rotation: 0, // Will be updated based on movement direction
      },
    };
  }

  private setupPolylineOptions() {
    this.polylineOptions = {
      strokeColor: '#00ff88',
      strokeOpacity: 0.7,
      strokeWeight: 2,
      icons: [{
        icon: {
          path: 'M 0,-1 0,1',
          strokeOpacity: 1,
          scale: 4
        },
        offset: '0',
        repeat: '20px'
      }]
    };
  }

  private async loadSatelliteData() {
    try {
      // Get satellite position
      const position = await this.getCurrentSatellitePosition();
      
      // Calculate rotation based on movement direction
      if (this.previousPosition) {
        const deltaLat = position.latitude - this.previousPosition.latitude;
        const deltaLng = position.longitude - this.previousPosition.longitude;
        
        // Calculate bearing angle in degrees (0° = North, 90° = East)
        // Adjust for coordinate system where top of icon should point in direction of movement
        const bearing = Math.atan2(deltaLng, deltaLat) * (180 / Math.PI);
        
        // Update marker options with new rotation - create new object to force re-render
        this.markerOptions = {
          icon: {
            path: 'M32,80 L88,80 Q100,80 100,92 L100,308 Q100,320 88,320 L32,320 Q20,320 20,308 L20,92 Q20,80 32,80 Z M40,80 L40,320 M60,80 L60,320 M80,80 L80,320 M20,100 L100,100 M20,120 L100,120 M20,140 L100,140 M20,160 L100,160 M20,180 L100,180 M20,200 L100,200 M20,220 L100,220 M20,240 L100,240 M20,260 L100,260 M20,280 L100,280 M20,300 L100,300 M312,80 L368,80 Q380,80 380,92 L380,308 Q380,320 368,320 L312,320 Q300,320 300,308 L300,92 Q300,80 312,80 Z M320,80 L320,320 M340,80 L340,320 M360,80 L360,320 M300,100 L380,100 M300,120 L380,120 M300,140 L380,140 M300,160 L380,160 M300,180 L380,180 M300,200 L380,200 M300,220 L380,220 M300,240 L380,240 M300,260 L380,260 M300,280 L380,280 M300,300 L380,300 M108,170 L142,170 Q150,170 150,178 L150,222 Q150,230 142,230 L108,230 Q100,230 100,222 L100,178 Q100,170 108,170 Z M258,170 L292,170 Q300,170 300,178 L300,222 Q300,230 292,230 L258,230 Q250,230 250,222 L250,178 Q250,170 258,170 Z M162,150 L238,150 Q250,150 250,162 L250,238 Q250,250 238,250 L162,250 Q150,250 150,238 L150,162 Q150,150 162,150 Z M175,200 A25,25 0 1,1 225,200 A25,25 0 1,1 175,200 M194,200 A6,6 0 1,1 206,200 A6,6 0 1,1 194,200 Z M222,220 L228,220 Q230,220 230,222 L230,226 Q230,228 228,228 L222,228 Q220,228 220,226 L220,222 Q220,220 222,220 Z M172,180 L180,180 Q182,180 182,182 L182,186 Q182,188 180,188 L172,188 Q170,188 170,186 L170,182 Q170,180 172,180 Z',
            fillOpacity: 0,
            strokeColor: '#ff4444',
            strokeWeight: 1,
            scale: 0.1,
            anchor: { x: 200, y: 200 },
            rotation: bearing,
          },
        };
      }
      
      // Store previous position for next calculation
      this.previousPosition = this.currentPosition;
      this.currentPosition = position;
      this.markerPosition = { lat: position.latitude, lng: position.longitude };
      
      // Load orbital path only once
      if (!this.orbitalPathLoaded) {
        const orbitalPath = await this.getOrbitalPath();
        this.pathPositions = orbitalPath.positions;
        this.orbitalPathLoaded = true;
      }
      
      this.cdr.detectChanges(); // Trigger change detection for data updates
    } catch (error) {
      console.error('Failed to load satellite data:', error);
    }
  }

  private async getCurrentSatellitePosition(): Promise<SatellitePosition> {
    // TODO: Replace with actual YAMCS API call to get satellite telemetry
    // This would typically get the satellite's current lat/lon/alt from telemetry
    
    // Mock data - smooth linear movement along orbit
    return new Promise((resolve) => {
      setTimeout(() => {
        // Increment simulation time for smooth movement
        this.simulationTime += 0.01; // Adjust speed as needed
        
        // Calculate position along a smooth orbital path
        const baseLatitude = 0; // Equatorial orbit
        const baseAltitude = 500; // 500km altitude
        
        resolve({
          latitude: baseLatitude + 25 * Math.sin(this.simulationTime), // Oscillate between -25 and 25 degrees
          longitude: (this.simulationTime * 50) % 360 - 180, // Move steadily west to east
          altitude: baseAltitude + 50 * Math.sin(this.simulationTime * 2), // Slight altitude variation
          timestamp: new Date(),
        });
      }, 100);
    });
  }

  private async getOrbitalPath(): Promise<OrbitalPath> {
    // TODO: Replace with actual YAMCS API call to get predicted orbital path
    // This would typically get the satellite's predicted positions for the next few orbits
    
    // Mock orbital path data
    return new Promise((resolve) => {
      setTimeout(() => {
        const positions: google.maps.LatLngLiteral[] = [];
        const currentPos = this.currentPosition;
        
        if (currentPos) {
          // Generate a simple orbital path around current position
          for (let i = 0; i < 100; i++) {
            const angle = (i / 100) * 2 * Math.PI;
            positions.push({
              lat: currentPos.latitude + 10 * Math.sin(angle),
              lng: currentPos.longitude + 20 * Math.cos(angle),
            });
          }
        }
        
        resolve({ positions });
      }, 300);
    });
  }


  onMapInitialized(_map: google.maps.Map) {
    // Map is initialized - we handle everything via Angular's reactive bindings
  }
}