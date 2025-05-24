'use client';

import { useState, useEffect } from 'react';
import { AlertCircle, CheckCircle, Loader, RefreshCw, Activity, Send, Server, Globe, Moon, Sun } from 'lucide-react';

const BASE_URL = 'http://localhost:8080';

// Type definitions
interface Status {
  isRunning: boolean;
  message: string;
  details?: string;
}

interface ULRFormData {
  imsi: string;
  plmnId: string;
  ratType: number;
  ulrFlags: number;
}

interface ValidationErrors {
  imsi?: string;
  plmnId?: string;
  ratType?: string;
  ulrFlags?: string;
}

interface ToastProps {
  message: string;
  type: 'success' | 'error';
  onClose: () => void;
}

// API service for handling API calls
const apiService = {
  // Get status of Diameter client
  getClientStatus: async (): Promise<Status> => {
    try {
      const response = await fetch(`${BASE_URL}/diameter/client/status`);

      if (!response.ok) {
        throw new Error(`Error: ${response.status}`);
      }

      const data = await response.json();
      console.log("Client status:", data);

      // Use the data directly if it's already in the correct format
      if ('isRunning' in data && 'message' in data) {
        return {
          isRunning: data.isRunning,
          message: data.message,
          details: data.details || undefined
        };
      }

      // Fall back to the old format if needed
      if (data.status) {
        return {
          isRunning: data.status.includes("running"),
          message: data.status,
          details: undefined
        };
      }

      // Default response if neither format matches
      throw new Error('Invalid response format from server');
    } catch (error) {
      if (error instanceof Error) {
        throw error;
      }
      throw new Error('Unknown error occurred');
    }
  },

  // Get status of Diameter server
  getServerStatus: async (): Promise<Status> => {
    try {
      const response = await fetch(`${BASE_URL}/diameter/server/status`);

      if (!response.ok) {
        throw new Error(`Error: ${response.status}`);
      }

      const data = await response.json();
      console.log("Server status:", data);

      // Use the data directly if it's already in the correct format
      if ('isRunning' in data && 'message' in data) {
        return {
          isRunning: data.isRunning,
          message: data.message,
          details: data.details || undefined
        };
      }

      // Fall back to the old format if needed
      if (data.status) {
        return {
          isRunning: data.status.includes("running"),
          message: data.status,
          details: undefined
        };
      }

      // Default response if neither format matches
      throw new Error('Invalid response format from server');
    } catch (error) {
      if (error instanceof Error) {
        throw error;
      }
      throw new Error('Unknown error occurred');
    }
  },

  // Send Update Location Request
  sendULR: async (data: ULRFormData) => {
    try {
      const response = await fetch(`${BASE_URL}/diameter/client/sendULR`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(data),
      });
      if (!response.ok) {
        throw new Error(`Error: ${response.status}`);
      }
      console.log(response);
      return await response.json();
    } catch (error) {
      if (error instanceof Error) {
        throw error;
      }
      throw new Error('Unknown error occurred');
    }
  },
};

// Toast notification component
const Toast = ({ message, type, onClose }: ToastProps) => {
  useEffect(() => {
    const timer = setTimeout(() => {
      onClose();
    }, 5000);
    return () => clearTimeout(timer);
  }, [onClose]);

  const bgColor = type === 'success' ? 'bg-emerald-800' : 'bg-rose-800';
  const borderColor = type === 'success' ? 'border-emerald-600' : 'border-rose-600';
  const textColor = type === 'success' ? 'text-emerald-200' : 'text-rose-200';
  const iconBgColor = type === 'success' ? 'bg-emerald-500' : 'bg-rose-500';
  const Icon = type === 'success' ? CheckCircle : AlertCircle;

  return (
      <div className={`fixed top-4 right-4 p-4 rounded-xl border ${borderColor} shadow-lg flex items-center gap-3 ${bgColor} backdrop-blur-sm z-50 animate-fadeIn`}>
        <div className={`${iconBgColor} p-2 rounded-full`}>
          <Icon className="text-black" size={20} />
        </div>
        <span className={`${textColor} font-medium`}>{message}</span>
        <button onClick={onClose} className="ml-4 text-gray-400 hover:text-gray-200 transition-colors">
          &times;
        </button>
      </div>
  );
};

// Status Card Component
interface StatusCardProps {
  title: string;
  icon: React.ReactNode;
  status: Status | null;
  isLoading: boolean;
  error: string | null;
}

const StatusCard = ({ title, icon, status, isLoading, error }: StatusCardProps) => {
  return (
      <div className="bg-gray-800 rounded-xl shadow-lg p-6 mb-6 border border-gray-700 hover:shadow-xl transition-shadow duration-300">
        <div className="flex items-center gap-2 mb-4">
          <div className="text-cyan-400">
            {icon}
          </div>
          <h2 className="text-xl font-bold text-gray-100">{title}</h2>
        </div>

        {isLoading ? (
            <div className="flex items-center space-x-2 text-gray-300 p-4 bg-gray-900 rounded-lg">
              <Loader size={20} className="animate-spin text-cyan-400" />
              <span>Loading status...</span>
            </div>
        ) : error ? (
            <div className="flex items-center space-x-2 text-rose-300 p-4 bg-rose-900/30 rounded-lg">
              <AlertCircle size={20} />
              <span>{error}</span>
            </div>
        ) : (
            <div className={`p-5 rounded-lg flex items-center ${status?.isRunning ? 'bg-gradient-to-r from-emerald-900/40 to-teal-900/40 border border-emerald-700/50' : 'bg-gradient-to-r from-rose-900/40 to-red-900/40 border border-rose-700/50'}`}>
              <div className={`mr-4 p-3 rounded-full ${status?.isRunning ? 'bg-emerald-500' : 'bg-rose-500'}`}>
                <Activity className="text-black" size={20} />
              </div>
              <div>
                <p className={`font-medium text-lg ${status?.isRunning ? 'text-emerald-300' : 'text-rose-300'}`}>
                  {status?.message || 'Status unknown'}
                </p>
                {status?.details && <p className="text-gray-400 mt-1">{status.details}</p>}
              </div>
            </div>
        )}
      </div>
  );
};

// ULR Form Component
interface ULRFormProps {
  onSubmit: (data: ULRFormData) => void;
  isLoading: boolean;
}

const ULRForm = ({ onSubmit, isLoading }: ULRFormProps) => {
  const [formData, setFormData] = useState<ULRFormData>({
    imsi: '310150123456789',
    plmnId: '310150',
    ratType: 1004,
    ulrFlags: 16777216,
  });

  const [validationErrors, setValidationErrors] = useState<ValidationErrors>({});

  const validateForm = (): boolean => {
    const errors: ValidationErrors = {};

    // IMSI validation
    if (!formData.imsi.trim()) {
      errors.imsi = 'IMSI is required';
    }

    // PLMN ID validation
    if (!formData.plmnId.trim()) {
      errors.plmnId = 'PLMN ID is required';
    } else if (!/^[0-9A-Fa-f]+$/.test(formData.plmnId)) {
      errors.plmnId = 'PLMN ID must be a valid hexadecimal string';
    }

    // RAT Type validation
    if (isNaN(formData.ratType) || formData.ratType < 0) {
      errors.ratType = 'RAT Type must be a valid integer';
    }

    // ULR Flags validation
    if (isNaN(formData.ulrFlags) || formData.ulrFlags < 0) {
      errors.ulrFlags = 'ULR Flags must be a valid integer';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData({
      ...formData,
      [name]: name === 'ratType' || name === 'ulrFlags' ? parseInt(value, 10) || 0 : value,
    });
  };

  const handleSubmit = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    if (validateForm()) {
      onSubmit(formData);
    }
  };

  return (
      <div className="bg-gray-800 rounded-xl shadow-lg p-6 border border-gray-700 hover:shadow-xl transition-shadow duration-300">
        <div className="flex items-center gap-2 mb-6">
          <Send className="text-cyan-400" size={24} />
          <h2 className="text-xl font-bold text-gray-100">Send Update Location Request (ULR)</h2>
        </div>

        <div>
          <div className="mb-5">
            <label htmlFor="imsi" className="block text-sm font-medium text-gray-300 mb-2">
              IMSI (International Mobile Subscriber Identity)
            </label>
            <input
                type="text"
                id="imsi"
                name="imsi"
                value={formData.imsi}
                onChange={handleChange}
                className={`w-full p-3 border bg-gray-900 text-gray-100 rounded-lg focus:ring-2 focus:ring-offset-1 focus:ring-offset-gray-900 ${
                    validationErrors.imsi ? 'border-rose-500 focus:ring-rose-500/40' : 'border-gray-700 focus:ring-cyan-500/40'
                } focus:border-cyan-500 outline-none transition-all`}
                placeholder="Enter IMSI"
            />
            {validationErrors.imsi && (
                <p className="mt-1 text-sm text-rose-400 flex items-center">
                  <AlertCircle size={14} className="mr-1" /> {validationErrors.imsi}
                </p>
            )}
          </div>

          <div className="mb-5">
            <label htmlFor="plmnId" className="block text-sm font-medium text-gray-300 mb-2">
              PLMN ID (Hexadecimal)
            </label>
            <input
                type="text"
                id="plmnId"
                name="plmnId"
                value={formData.plmnId}
                onChange={handleChange}
                className={`w-full p-3 border bg-gray-900 text-gray-100 rounded-lg focus:ring-2 focus:ring-offset-1 focus:ring-offset-gray-900 ${
                    validationErrors.plmnId ? 'border-rose-500 focus:ring-rose-500/40' : 'border-gray-700 focus:ring-cyan-500/40'
                } focus:border-cyan-500 outline-none transition-all`}
                placeholder="Enter PLMN ID (hex)"
            />
            {validationErrors.plmnId && (
                <p className="mt-1 text-sm text-rose-400 flex items-center">
                  <AlertCircle size={14} className="mr-1" /> {validationErrors.plmnId}
                </p>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-5 mb-6">
            <div>
              <label htmlFor="ratType" className="block text-sm font-medium text-gray-300 mb-2">
                RAT Type
              </label>
              <input
                  type="number"
                  id="ratType"
                  name="ratType"
                  value={formData.ratType}
                  onChange={handleChange}
                  min="0"
                  className={`w-full p-3 border bg-gray-900 text-gray-100 rounded-lg focus:ring-2 focus:ring-offset-1 focus:ring-offset-gray-900 ${
                      validationErrors.ratType ? 'border-rose-500 focus:ring-rose-500/40' : 'border-gray-700 focus:ring-cyan-500/40'
                  } focus:border-cyan-500 outline-none transition-all`}
              />
              {validationErrors.ratType && (
                  <p className="mt-1 text-sm text-rose-400 flex items-center">
                    <AlertCircle size={14} className="mr-1" /> {validationErrors.ratType}
                  </p>
              )}
            </div>

            <div>
              <label htmlFor="ulrFlags" className="block text-sm font-medium text-gray-300 mb-2">
                ULR Flags
              </label>
              <input
                  type="number"
                  id="ulrFlags"
                  name="ulrFlags"
                  value={formData.ulrFlags}
                  onChange={handleChange}
                  min="0"
                  className={`w-full p-3 border bg-gray-900 text-gray-100 rounded-lg focus:ring-2 focus:ring-offset-1 focus:ring-offset-gray-900 ${
                      validationErrors.ulrFlags ? 'border-rose-500 focus:ring-rose-500/40' : 'border-gray-700 focus:ring-cyan-500/40'
                  } focus:border-cyan-500 outline-none transition-all`}
              />
              {validationErrors.ulrFlags && (
                  <p className="mt-1 text-sm text-rose-400 flex items-center">
                    <AlertCircle size={14} className="mr-1" /> {validationErrors.ulrFlags}
                  </p>
              )}
            </div>
          </div>

          <button
              onClick={handleSubmit}
              disabled={isLoading}
              className="w-full bg-gradient-to-r from-cyan-600 to-blue-600 text-white py-3 px-6 rounded-lg hover:from-cyan-700 hover:to-blue-700 transition duration-300 disabled:opacity-70 disabled:cursor-not-allowed flex justify-center items-center shadow-md hover:shadow-lg font-medium"
          >
            {isLoading ? (
                <>
                  <Loader size={20} className="animate-spin mr-2" />
                  Sending...
                </>
            ) : (
                <>
                  <Send size={18} className="mr-2" />
                  Send ULR
                </>
            )}
          </button>
        </div>
      </div>
  );
};

// Main App Component
export default function DiameterClientPage() {
  const [darkMode, setDarkMode] = useState<boolean>(true);
  const [clientStatus, setClientStatus] = useState<Status | null>(null);
  const [serverStatus, setServerStatus] = useState<Status | null>(null);
  const [clientStatusLoading, setClientStatusLoading] = useState<boolean>(false);
  const [serverStatusLoading, setServerStatusLoading] = useState<boolean>(false);
  const [clientStatusError, setClientStatusError] = useState<string | null>(null);
  const [serverStatusError, setServerStatusError] = useState<string | null>(null);
  const [ulrLoading, setUlrLoading] = useState<boolean>(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  // Fetch client status on component mount
  useEffect(() => {
    fetchClientStatus();
    fetchServerStatus();
  }, []);

  const fetchClientStatus = async () => {
    setClientStatusLoading(true);
    setClientStatusError(null);
    try {
      const data = await apiService.getClientStatus();
      setClientStatus(data);
    } catch (error) {
      let errorMessage = 'Failed to fetch client status';
      if (error instanceof Error) {
        errorMessage = error.message;
      }
      setClientStatusError(errorMessage);
    } finally {
      setClientStatusLoading(false);
    }
  };

  const fetchServerStatus = async () => {
    setServerStatusLoading(true);
    setServerStatusError(null);
    try {
      const data = await apiService.getServerStatus();
      setServerStatus(data);
    } catch (error) {
      let errorMessage = 'Failed to fetch server status';
      if (error instanceof Error) {
        errorMessage = error.message;
      }
      setServerStatusError(errorMessage);
    } finally {
      setServerStatusLoading(false);
    }
  };

  const handleSendULR = async (data: ULRFormData) => {
    setUlrLoading(true);
    try {
      const response = await apiService.sendULR(data);
      setToast({
        message: 'ULR sent successfully',
        type: 'success',
      });
      // Refresh status after sending ULR
      fetchClientStatus();
      fetchServerStatus();
    } catch (error) {
      let errorMessage = 'Failed to send ULR';
      if (error instanceof Error) {
        errorMessage = error.message;
      }
      setToast({
        message: errorMessage,
        type: 'error',
      });
    } finally {
      setUlrLoading(false);
    }
  };

  const dismissToast = () => {
    setToast(null);
  };

  const toggleTheme = () => {
    setDarkMode(!darkMode);
  };

  return (
      <div className={`min-h-screen ${darkMode ? 'bg-gradient-to-br from-gray-900 to-slate-900' : 'bg-gradient-to-br from-slate-50 to-blue-50'} py-12 px-4`}>
        <div className="max-w-4xl mx-auto">
          <header className="mb-10 text-center relative">
            <button
                onClick={toggleTheme}
                className={`absolute right-0 top-0 p-2 rounded-full transition-colors ${darkMode ? 'bg-gray-800 hover:bg-gray-700' : 'bg-white hover:bg-gray-100'} shadow-md`}
            >
              {darkMode ? <Sun className="text-amber-400" size={24} /> : <Moon className="text-indigo-600" size={24} />}
            </button>

            <div className={`inline-block ${darkMode ? 'bg-gradient-to-r from-cyan-400 to-blue-400' : 'bg-gradient-to-r from-indigo-600 to-violet-600'} text-transparent bg-clip-text`}>
              <h1 className="text-4xl font-bold mb-2">Diameter Traffic Simulator</h1>
            </div>
            <p className={`${darkMode ? 'text-gray-400' : 'text-gray-600'} mt-2 max-w-xl mx-auto`}>
              Simulate Diameter Traffic Just like a Real One
            </p>
            <div className={`h-1 w-20 ${darkMode ? 'bg-gradient-to-r from-cyan-400 to-blue-400' : 'bg-gradient-to-r from-indigo-600 to-violet-600'} mx-auto mt-4 rounded-full`}></div>
          </header>

          <main className="grid grid-cols-1 gap-8">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <StatusCard
                  title="Client Status"
                  icon={<Server size={24} />}
                  status={clientStatus}
                  isLoading={clientStatusLoading}
                  error={clientStatusError}
              />

              <StatusCard
                  title="Server Status"
                  icon={<Globe size={24} />}
                  status={serverStatus}
                  isLoading={serverStatusLoading}
                  error={serverStatusError}
              />
            </div>

            <div className="flex justify-end mb-2 gap-3">
              <button
                  onClick={() => { fetchClientStatus(); fetchServerStatus(); }}
                  disabled={clientStatusLoading || serverStatusLoading}
                  className={`flex items-center ${
                      darkMode
                          ? 'bg-gray-800 hover:bg-gray-700 text-cyan-400 border-gray-700'
                          : 'bg-white hover:bg-gray-50 text-indigo-700 border-gray-200'
                  } py-2 px-5 rounded-lg transition-all duration-300 shadow-sm hover:shadow border font-medium`}
              >
                {(clientStatusLoading || serverStatusLoading) ? (
                    <Loader size={16} className={`animate-spin mr-2 ${darkMode ? 'text-cyan-400' : 'text-indigo-600'}`} />
                ) : (
                    <RefreshCw size={16} className={`mr-2 ${darkMode ? 'text-cyan-400' : 'text-indigo-600'}`} />
                )}
                Refresh Status
              </button>
            </div>

            <ULRForm onSubmit={handleSendULR} isLoading={ulrLoading} />
          </main>

          <footer className="mt-16 text-center">
            <div className="h-px w-full bg-gradient-to-r from-transparent via-gray-700 to-transparent mb-6"></div>
            <p className="text-gray-500 text-sm">© 2025 Diameter Client Interface</p>
          </footer>

          {toast && <Toast message={toast.message} type={toast.type} onClose={dismissToast} />}
        </div>
      </div>
  );
}