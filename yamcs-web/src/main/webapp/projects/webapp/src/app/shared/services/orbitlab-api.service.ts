import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map, catchError } from 'rxjs/operators';

// OrbitLab API response format (matches their API exactly)
interface OrbitLabApiResponse {
  Latitude: number;
  Longitude: number;
  Altitude: number;
  Timestamp: string;
}

// Internal application format (reusing existing interface)
export interface SatellitePosition {
  latitude: number;
  longitude: number;
  altitude: number;
  timestamp: Date;
}

@Injectable({
  providedIn: 'root'
})
export class OrbitLabApiService {
  private readonly baseUrl = 'https://dev.orbitlab.tm2.space/api/orbitlab';

  constructor(private http: HttpClient) {}

  /**
   * Get satellite positions from OrbitLab API
   * API keys are retrieved securely from environment/config service
   */
  getSatellitePositions(): Observable<SatellitePosition[]> {
    const apiKey = this.getApiKey();
    const satelliteKey = this.getSatelliteKey();

    if (!apiKey || !satelliteKey) {
      return throwError(() => new Error('OrbitLab API credentials not configured'));
    }

    const headers = new HttpHeaders({
      'accept': 'application/json',
      'Authorization': `Bearer ${apiKey}`
    });

    const url = `${this.baseUrl}/satellites/${satelliteKey}/positions`;

    return this.http.get<OrbitLabApiResponse[]>(url, { headers }).pipe(
      map((apiResponse: OrbitLabApiResponse[]) => 
        apiResponse.map(pos => this.transformApiResponse(pos))
      ),
      catchError((error) => {
        console.error('OrbitLab API error:', error);
        return throwError(() => new Error('Failed to fetch satellite data from OrbitLab'));
      })
    );
  }

  /**
   * Get current satellite position based on current UTC time
   */
  getCurrentPosition(positions: SatellitePosition[]): SatellitePosition | null {
    if (!positions || positions.length === 0) {
      return null;
    }

    const now = new Date();
    
    // Find the position closest to current time
    let closestPosition = positions[0];
    let minTimeDiff = Math.abs(now.getTime() - closestPosition.timestamp.getTime());

    for (const position of positions) {
      const timeDiff = Math.abs(now.getTime() - position.timestamp.getTime());
      if (timeDiff < minTimeDiff) {
        minTimeDiff = timeDiff;
        closestPosition = position;
      }
    }

    return closestPosition;
  }

  /**
   * Filter positions to show next 3 hours from current time
   */
  getOrbitalPath(positions: SatellitePosition[]): SatellitePosition[] {
    if (!positions || positions.length === 0) {
      return [];
    }

    const now = new Date();
    const threeHoursFromNow = new Date(now.getTime() + (3 * 60 * 60 * 1000));

    return positions.filter(pos => {
      const posTime = pos.timestamp.getTime();
      return posTime >= now.getTime() && posTime <= threeHoursFromNow.getTime();
    });
  }

  /**
   * Transform OrbitLab API response to internal SatellitePosition format
   */
  private transformApiResponse(apiResponse: OrbitLabApiResponse): SatellitePosition {
    return {
      latitude: apiResponse.Latitude,
      longitude: apiResponse.Longitude,
      altitude: apiResponse.Altitude,
      timestamp: new Date(apiResponse.Timestamp)
    };
  }

  /**
   * Securely retrieve API key from window configuration
   */
  private getApiKey(): string | null {
    // Check window configuration injected at runtime
    const windowApiKey = (window as any).ORBITLAB_API_KEY;
    return windowApiKey || null;
  }

  /**
   * Securely retrieve satellite key from window configuration
   */
  private getSatelliteKey(): string | null {
    // Check window configuration injected at runtime  
    const windowSatelliteKey = (window as any).ORBITLAB_API_SATELLITE_KEY;
    return windowSatelliteKey || null;
  }
}