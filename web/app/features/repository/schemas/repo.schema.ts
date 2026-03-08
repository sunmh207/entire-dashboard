/**
 * Repository module - Valibot validation schema
 */

import * as v from 'valibot'

/** Repository create/edit form validation schema */
export const repoFormSchema = v.object({
  name: v.pipe(
    v.string('Name must be a string'),
    v.minLength(1, 'Name is required'),
    v.maxLength(255, 'Name cannot exceed 255 characters'),
  ),
  webUrl: v.pipe(
    v.string('Web URL must be a string'),
    v.url('Please enter a valid URL'),
  ),
  platform: v.pipe(
    v.string('Platform must be a string'),
    v.minLength(1, 'Please select a platform'),
  ),
  accessToken: v.optional(v.string()),
})
