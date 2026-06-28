import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { useAuthStore } from '../store/index.js';

/**
 * Concept #21 — Security: Zod schema validation, password policy
 * Concept #3  — TypeScript: discriminated unions for form state
 */

const RegisterSchema = z.object({
  username: z.string().min(3).max(30).regex(/^[a-zA-Z0-9_-]+$/, 'Letters, numbers, _ and - only'),
  email: z.string().email('Invalid email address'),
  password: z.string()
    .min(8, 'At least 8 characters')
    .regex(/[A-Z]/, 'At least one uppercase letter')
    .regex(/[a-z]/, 'At least one lowercase letter')
    .regex(/[0-9]/, 'At least one digit'),
});

type FormErrors = Partial<Record<keyof z.infer<typeof RegisterSchema>, string>>;

export default function RegisterPage(): React.ReactElement {
  const navigate = useNavigate();
  const { register, isLoading, error } = useAuthStore();

  const [form, setForm] = useState({ username: '', email: '', password: '' });
  const [errors, setErrors] = useState<FormErrors>({});

  const handleChange = (field: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm(prev => ({ ...prev, [field]: e.target.value }));
    setErrors(prev => ({ ...prev, [field]: undefined }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const result = RegisterSchema.safeParse(form);
    if (!result.success) {
      const fieldErrors: FormErrors = {};
      for (const issue of result.error.issues) {
        const field = issue.path[0] as keyof FormErrors;
        fieldErrors[field] = issue.message;
      }
      setErrors(fieldErrors);
      return;
    }

    try {
      await register(form.username, form.email, form.password);
      navigate('/dashboard');
    } catch {
      // error is in store
    }
  };

  const fields: Array<{ id: keyof typeof form; label: string; type: string; placeholder: string }> = [
    { id: 'username', label: 'Username', type: 'text', placeholder: 'john_doe' },
    { id: 'email',    label: 'Email',    type: 'email', placeholder: 'you@example.com' },
    { id: 'password', label: 'Password', type: 'password', placeholder: '••••••••' },
  ];

  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white">ConceptualWare</h1>
          <p className="text-gray-400 mt-1 text-sm">Developer Intelligence Platform</p>
        </div>

        <div className="bg-gray-900 border border-gray-800 rounded-2xl p-8">
          <h2 className="text-xl font-semibold text-white mb-6">Create account</h2>

          {error && (
            <div className="bg-red-500/10 border border-red-500/30 text-red-400 text-sm rounded-lg px-4 py-3 mb-4">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {fields.map(({ id, label, type, placeholder }) => (
              <div key={id}>
                <label htmlFor={id} className="block text-sm font-medium text-gray-300 mb-1.5">
                  {label}
                </label>
                <input
                  id={id}
                  type={type}
                  value={form[id]}
                  onChange={handleChange(id)}
                  autoComplete={type === 'password' ? 'new-password' : id}
                  className={`w-full bg-gray-800 border rounded-lg px-3 py-2.5 text-white text-sm placeholder-gray-500 focus:outline-none transition-colors ${
                    errors[id] ? 'border-red-500' : 'border-gray-700 focus:border-blue-500'
                  }`}
                  placeholder={placeholder}
                />
                {errors[id] && (
                  <p className="text-red-400 text-xs mt-1">{errors[id]}</p>
                )}
              </div>
            ))}

            <button
              type="submit"
              disabled={isLoading}
              className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-medium py-2.5 rounded-lg text-sm transition-colors mt-2"
            >
              {isLoading ? 'Creating account...' : 'Create account'}
            </button>
          </form>

          <p className="text-center text-sm text-gray-500 mt-6">
            Already have an account?{' '}
            <Link to="/login" className="text-blue-400 hover:text-blue-300">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
