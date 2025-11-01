"""
Configuration loader for WiFi GeoGrabber Python tools
Loads settings from environment variables with sensible defaults
"""

import os
from dotenv import load_dotenv
from pathlib import Path

# Load .env file from project root
env_path = Path(__file__).parent.parent / '.env'
load_dotenv(dotenv_path=env_path)


class Config:
    """Configuration loader for WiFi GeoGrabber"""
    
    def __init__(self):
        # Database settings
        self.DB_NAME = os.getenv('DB_NAME', 'wifi_scanner.db')
        self.MAX_DB_SIZE_MB = int(os.getenv('MAX_DB_SIZE_MB', '100'))
        self.MAX_QUERY_RECORDS = int(os.getenv('MAX_QUERY_RECORDS', '50000'))
        
        # Security settings
        self.DEFAULT_CHECKSUM_VERIFICATION = os.getenv('DEFAULT_CHECKSUM_VERIFICATION', 'true').lower() == 'true'
        self.CHECKSUM_ALGORITHM = os.getenv('CHECKSUM_ALGORITHM', 'SHA-256')
        
        # Map settings
        self.DEFAULT_MAP_ZOOM = int(os.getenv('DEFAULT_MAP_ZOOM', '15'))
        self.MAP_TILE_PROVIDER = os.getenv('MAP_TILE_PROVIDER', 'OpenStreetMap')
        
        # Logging
        self.LOG_LEVEL = os.getenv('LOG_LEVEL', 'INFO').upper()
        self.LOG_TO_FILE = os.getenv('LOG_TO_FILE', 'false').lower() == 'true'
        self.LOG_FILE_PATH = os.getenv('LOG_FILE_PATH', './logs/geograbber.log')
        
        # Optional API keys (for future use)
        self.GOOGLE_MAPS_API_KEY = os.getenv('GOOGLE_MAPS_API_KEY')
        self.OSM_TILE_SERVER = os.getenv('OSM_TILE_SERVER', 'https://tile.openstreetmap.org/{z}/{x}/{y}.png')
        self.WIGLE_API_KEY = os.getenv('WIGLE_API_KEY')
        
        # Firebase (for future cloud sync)
        self.FIREBASE_PROJECT_ID = os.getenv('FIREBASE_PROJECT_ID')
        self.FIREBASE_API_KEY = os.getenv('FIREBASE_API_KEY')
        
        # AWS (for future cloud storage)
        self.AWS_ACCESS_KEY_ID = os.getenv('AWS_ACCESS_KEY_ID')
        self.AWS_SECRET_ACCESS_KEY = os.getenv('AWS_SECRET_ACCESS_KEY')
        self.AWS_REGION = os.getenv('AWS_REGION', 'us-east-1')
        self.AWS_S3_BUCKET = os.getenv('AWS_S3_BUCKET')
        
        # Debug mode
        self.DEBUG_MODE = os.getenv('DEBUG_MODE', 'false').lower() == 'true'
        self.VERBOSE_LOGGING = os.getenv('VERBOSE_LOGGING', 'false').lower() == 'true'
    
    def validate(self):
        """Validate configuration"""
        errors = []
        
        # Validate database size
        if self.MAX_DB_SIZE_MB < 1 or self.MAX_DB_SIZE_MB > 1000:
            errors.append("MAX_DB_SIZE_MB must be between 1 and 1000")
        
        # Validate max query records
        if self.MAX_QUERY_RECORDS < 100 or self.MAX_QUERY_RECORDS > 1000000:
            errors.append("MAX_QUERY_RECORDS must be between 100 and 1,000,000")
        
        # Validate map zoom
        if self.DEFAULT_MAP_ZOOM < 1 or self.DEFAULT_MAP_ZOOM > 20:
            errors.append("DEFAULT_MAP_ZOOM must be between 1 and 20")
        
        # Validate log level
        valid_log_levels = ['DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL']
        if self.LOG_LEVEL not in valid_log_levels:
            errors.append(f"LOG_LEVEL must be one of: {', '.join(valid_log_levels)}")
        
        if errors:
            raise ValueError(f"Configuration errors:\n  - " + "\n  - ".join(errors))
        
        return True
    
    def get_map_tile_url(self):
        """Get the appropriate map tile URL based on configuration"""
        if self.GOOGLE_MAPS_API_KEY:
            # Use Google Maps if API key is available
            return f"https://mt1.google.com/vt/lyrs=m&x={{x}}&y={{y}}&z={{z}}&key={self.GOOGLE_MAPS_API_KEY}"
        else:
            # Use OpenStreetMap by default
            return self.OSM_TILE_SERVER
    
    def has_api_key(self, service: str) -> bool:
        """Check if an API key is configured for a specific service"""
        key_map = {
            'google_maps': self.GOOGLE_MAPS_API_KEY,
            'wigle': self.WIGLE_API_KEY,
            'firebase': self.FIREBASE_API_KEY,
            'aws': self.AWS_ACCESS_KEY_ID and self.AWS_SECRET_ACCESS_KEY
        }
        return bool(key_map.get(service.lower()))
    
    def __repr__(self):
        """String representation (with masked secrets)"""
        def mask_secret(value):
            if value and len(value) > 8:
                return f"{value[:4]}...{value[-4:]}"
            elif value:
                return "***"
            return None
        
        return f"""Config(
    DB_NAME={self.DB_NAME},
    MAX_DB_SIZE_MB={self.MAX_DB_SIZE_MB},
    MAX_QUERY_RECORDS={self.MAX_QUERY_RECORDS},
    CHECKSUM_ALGORITHM={self.CHECKSUM_ALGORITHM},
    DEFAULT_MAP_ZOOM={self.DEFAULT_MAP_ZOOM},
    MAP_TILE_PROVIDER={self.MAP_TILE_PROVIDER},
    LOG_LEVEL={self.LOG_LEVEL},
    GOOGLE_MAPS_API_KEY={mask_secret(self.GOOGLE_MAPS_API_KEY)},
    WIGLE_API_KEY={mask_secret(self.WIGLE_API_KEY)},
    DEBUG_MODE={self.DEBUG_MODE}
)"""


# Global config instance
config = Config()

# Validate on import (will raise ValueError if invalid)
try:
    config.validate()
except ValueError as e:
    print(f"⚠️  Configuration Warning: {e}")
    print("Using default values. Create a .env file to customize settings.")


# Convenience functions
def get_config():
    """Get the global config instance"""
    return config


def reload_config():
    """Reload configuration from environment"""
    global config
    load_dotenv(dotenv_path=env_path, override=True)
    config = Config()
    config.validate()
    return config


if __name__ == "__main__":
    # Test configuration
    print("=== WiFi GeoGrabber Configuration ===")
    print(config)
    print("\n=== Validation ===")
    try:
        config.validate()
        print("✅ Configuration is valid")
    except ValueError as e:
        print(f"❌ Configuration errors:\n{e}")
    
    print("\n=== API Keys Status ===")
    print(f"Google Maps: {'✅ Configured' if config.has_api_key('google_maps') else '❌ Not configured'}")
    print(f"Wigle.net: {'✅ Configured' if config.has_api_key('wigle') else '❌ Not configured'}")
    print(f"Firebase: {'✅ Configured' if config.has_api_key('firebase') else '❌ Not configured'}")
    print(f"AWS: {'✅ Configured' if config.has_api_key('aws') else '❌ Not configured'}")
