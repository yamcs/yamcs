import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class MapsConfigService {

  /**
   * Get Google Maps API key from configuration.
   * In production, this should be set via YAMCS configuration or environment variables.
   * For development/demo purposes, using the provided key.
   */
  getGoogleMapsApiKey(): string {
    const windowKey = (window as any).GOOGLE_MAPS_API_KEY;
    return windowKey;
  }

  /**
   * Initialize Google Maps with the configured API key
   */
  async initializeGoogleMaps(): Promise<void> {
    const apiKey = this.getGoogleMapsApiKey();
    
    if (!apiKey) {
      console.warn('Google Maps API key not configured. Map features will not work.');
      throw new Error('Google Maps API key not configured');
    }

    // Check if Google Maps is already loaded
    if (typeof google !== 'undefined' && google.maps) {
      return Promise.resolve();
    }

    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = `https://maps.googleapis.com/maps/api/js?key=${apiKey}&libraries=geometry&v=weekly`;
      script.async = true;
      script.defer = true;
      
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Failed to load Google Maps API'));
      
      document.head.appendChild(script);
    });
  }
}