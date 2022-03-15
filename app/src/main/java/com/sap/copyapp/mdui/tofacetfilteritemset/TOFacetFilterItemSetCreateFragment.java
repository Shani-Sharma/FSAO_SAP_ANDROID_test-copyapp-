package com.sap.copyapp.mdui.tofacetfilteritemset;

import androidx.lifecycle.ViewModelProvider;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.sap.copyapp.R;
import com.sap.copyapp.databinding.FragmentTofacetfilteritemsetCreateBinding;
import com.sap.copyapp.mdui.BundleKeys;
import com.sap.copyapp.mdui.InterfacedFragment;
import com.sap.copyapp.mdui.UIConstants;
import com.sap.copyapp.repository.OperationResult;
import com.sap.copyapp.viewmodel.tofacetfilteritem.TOFacetFilterItemViewModel;
import com.sap.cloud.mobile.fiori.object.ObjectHeader;
import com.sap.cloud.android.odata.eam_ntf_create_entities.TOFacetFilterItem;
import com.sap.cloud.android.odata.eam_ntf_create_entities.EAM_NTF_CREATE_EntitiesMetadata.EntityTypes;
import com.sap.cloud.android.odata.eam_ntf_create_entities.EAM_NTF_CREATE_EntitiesMetadata.EntitySets;
import com.sap.cloud.mobile.fiori.formcell.SimplePropertyFormCell;
import com.sap.cloud.mobile.odata.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A fragment that presents a screen to either create or update an existing TOFacetFilterItem entity.
 * This fragment is contained in the {@link TOFacetFilterItemSetActivity}.
 */
public class TOFacetFilterItemSetCreateFragment extends InterfacedFragment<TOFacetFilterItem> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TOFacetFilterItemSetCreateFragment.class);
    //The key for the saved instance of the working entity for device configuration change
    private static final String KEY_WORKING_COPY = "WORKING_COPY";

    /** TOFacetFilterItem object and it's copy: the modifications are done on the copied object. */
    private TOFacetFilterItem tOFacetFilterItemEntity;
    private TOFacetFilterItem tOFacetFilterItemEntityCopy;

    /** DataBinding generated class */
    private FragmentTofacetfilteritemsetCreateBinding binding;

    /** Indicate what operation to be performed */
    private String operation;

    /** TOFacetFilterItem ViewModel */
    private TOFacetFilterItemViewModel viewModel;

    /** The update menu item */
    private MenuItem updateMenuItem;

    /**
     * This fragment is used for both update and create for TOFacetFilterItemSet to enter values for the properties.
     * When used for update, an instance of the entity is required. In the case of create, a new instance
     * of the entity with defaults will be created. The default values may not be acceptable for the
     * OData service.
     * Arguments: Operation: [OP_CREATE | OP_UPDATE]
     *            TOFacetFilterItem if Operation is update
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        menu = R.menu.itemlist_edit_options;
        setHasOptionsMenu(true);

        Bundle bundle = getArguments();
        operation = bundle.getString(BundleKeys.OPERATION);
        if (UIConstants.OP_CREATE.equals(operation)) {
            activityTitle = currentActivity.getResources().getString(R.string.title_create_fragment, EntityTypes.toFacetFilterItem.getLocalName());
        } else {
            activityTitle = currentActivity.getResources().getString(R.string.title_update_fragment) + " " + EntityTypes.toFacetFilterItem.getLocalName();
        }

        ((TOFacetFilterItemSetActivity)currentActivity).isNavigationDisabled = true;
        viewModel = new ViewModelProvider(currentActivity).get(TOFacetFilterItemViewModel.class);
        viewModel.getCreateResult().observe(this, result -> onComplete(result));
        viewModel.getUpdateResult().observe(this, result -> onComplete(result));

        if(UIConstants.OP_CREATE.equals(operation)) {
            tOFacetFilterItemEntity = createTOFacetFilterItem();
        } else {
            tOFacetFilterItemEntity = viewModel.getSelectedEntity().getValue();
        }

        TOFacetFilterItem workingCopy = null;
        if( savedInstanceState != null ) {
            workingCopy =  (TOFacetFilterItem)savedInstanceState.getParcelable(KEY_WORKING_COPY);
        }
        if( workingCopy == null ) {
            tOFacetFilterItemEntityCopy = (TOFacetFilterItem) tOFacetFilterItemEntity.copy();
            tOFacetFilterItemEntityCopy.setEntityTag(tOFacetFilterItemEntity.getEntityTag());
            tOFacetFilterItemEntityCopy.setOldEntity(tOFacetFilterItemEntity);
            tOFacetFilterItemEntityCopy.setEditLink((tOFacetFilterItemEntity.getEditLink()));
        } else {
            //in this case, the old entity and entity tag should already been set.
            tOFacetFilterItemEntityCopy = workingCopy;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ObjectHeader objectHeader = currentActivity.findViewById(R.id.objectHeader);
        if( objectHeader != null ) objectHeader.setVisibility(View.GONE);
        return setupDataBinding(inflater, container);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putParcelable(KEY_WORKING_COPY, tOFacetFilterItemEntityCopy);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if(secondaryToolbar != null) {
            secondaryToolbar.setTitle(activityTitle);
        } else {
            getActivity().setTitle(activityTitle);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.save_item:
                updateMenuItem = item;
                enableUpdateMenuItem(false);
                return onSaveItem();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** 
     * Enables or disables the update menu item base on the given 'enable'
     * @param enable true to enable the menu item, false otherwise
     */
    private void enableUpdateMenuItem(boolean enable) {
        updateMenuItem.setEnabled(enable);
        updateMenuItem.getIcon().setAlpha( enable ? 255 : 130);
    }

    /**
     * Saves the entity
     */
    private boolean onSaveItem() {
        if (!isTOFacetFilterItemValid()) {
            return false;
        }
        //set 'isNavigationDisabled' false here to make sure the logic in list is ok, and set it to true if update fails.
        ((TOFacetFilterItemSetActivity)currentActivity).isNavigationDisabled = false;
        if( progressBar != null ) progressBar.setVisibility(View.VISIBLE);
        if (operation.equals(UIConstants.OP_CREATE)) {
            viewModel.create(tOFacetFilterItemEntityCopy);
        } else {
            viewModel.update(tOFacetFilterItemEntityCopy);
        }
        return true;
    }

    /**
     * Create a new TOFacetFilterItem instance and initialize properties to its default values
     * Nullable property will remain null
     * @return new TOFacetFilterItem instance
     */
    private TOFacetFilterItem createTOFacetFilterItem() {
        TOFacetFilterItem tOFacetFilterItemEntity = new TOFacetFilterItem(true);
        return tOFacetFilterItemEntity;
    }

    /** Callback function to complete processing when updateResult or createResult events fired */
    private void onComplete(@NonNull OperationResult<TOFacetFilterItem> result) {
        if( progressBar != null ) progressBar.setVisibility(View.INVISIBLE);
        enableUpdateMenuItem(true);
        if (result.getError() != null) {
            ((TOFacetFilterItemSetActivity)currentActivity).isNavigationDisabled = true;
            handleError(result);
        } else {
            boolean isMasterDetail = currentActivity.getResources().getBoolean(R.bool.two_pane);
            if( UIConstants.OP_UPDATE.equals(operation) && !isMasterDetail) {
                viewModel.setSelectedEntity(tOFacetFilterItemEntityCopy);
            }
            currentActivity.onBackPressed();
        }
    }

    /** Simple validation: checks the presence of mandatory fields. */
    private boolean isValidProperty(@NonNull Property property, @NonNull String value) {
        boolean isValid = true;
        if (!property.isNullable() && value.isEmpty()) {
            isValid = false;
        }
        return isValid;
    }

    /**
     * Set up data binding for this view
     * @param inflater - layout inflater from onCreateView
     * @param container - view group from onCreateView
     * @return view - rootView from generated data binding code
     */
    private View setupDataBinding(@NonNull LayoutInflater inflater, ViewGroup container) {
        binding = FragmentTofacetfilteritemsetCreateBinding.inflate(inflater, container, false);
        View rootView = binding.getRoot();
        binding.setTOFacetFilterItem(tOFacetFilterItemEntityCopy);
        return rootView;
    }

    /** Validate the edited inputs */
    private boolean isTOFacetFilterItemValid() {
        LinearLayout linearLayout = getView().findViewById(R.id.create_update_tofacetfilteritem);
        boolean isValid = true;
        // validate properties i.e. check non-nullable properties are truly non-null
        for (int i = 0; i < linearLayout.getChildCount(); i++) {
            View viewItem = linearLayout.getChildAt(i);
            SimplePropertyFormCell simplePropertyFormCell = (SimplePropertyFormCell)viewItem;
            String propertyName = (String) simplePropertyFormCell.getTag();
            Property property = EntityTypes.toFacetFilterItem.getProperty(propertyName);
            String value = simplePropertyFormCell.getValue().toString();
            if (!isValidProperty(property, value)) {
                simplePropertyFormCell.setTag(R.id.TAG_HAS_MANDATORY_ERROR, true);
                String errorMessage = getResources().getString(R.string.mandatory_warning);
                simplePropertyFormCell.setErrorEnabled(true);
                simplePropertyFormCell.setError(errorMessage);
                isValid = false;
            }
            else {
                if (simplePropertyFormCell.isErrorEnabled()){
                    boolean hasMandatoryError = (Boolean)simplePropertyFormCell.getTag(R.id.TAG_HAS_MANDATORY_ERROR);
                    if (!hasMandatoryError) {
                        isValid = false;
                    } else {
                        simplePropertyFormCell.setErrorEnabled(false);
                    }
                }
                simplePropertyFormCell.setTag(R.id.TAG_HAS_MANDATORY_ERROR, false);
            }
        }
        return isValid;
    }

    /**
     * Notify user of error encountered while execution the operation
     * @param result - operation result with error
     */
    private void handleError(@NonNull OperationResult<TOFacetFilterItem> result) {
        String errorMessage;
        switch (result.getOperation()) {
            case UPDATE:
                errorMessage = getResources().getString(R.string.update_failed_detail);
                break;
            case CREATE:
                errorMessage = getResources().getString(R.string.create_failed_detail);
                break;
            default:
                throw new AssertionError();
        }
        showError(errorMessage);
    }
}
