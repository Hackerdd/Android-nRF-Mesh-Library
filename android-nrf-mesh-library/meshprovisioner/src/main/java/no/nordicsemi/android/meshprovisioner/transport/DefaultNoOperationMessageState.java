package no.nordicsemi.android.meshprovisioner.transport;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;

import no.nordicsemi.android.meshprovisioner.control.BlockAcknowledgementMessage;
import no.nordicsemi.android.meshprovisioner.control.TransportControlMessage;
import no.nordicsemi.android.meshprovisioner.opcodes.ApplicationMessageOpCodes;
import no.nordicsemi.android.meshprovisioner.opcodes.ConfigMessageOpCodes;
import no.nordicsemi.android.meshprovisioner.utils.AddressUtils;
import no.nordicsemi.android.meshprovisioner.utils.MeshParserUtils;
import no.nordicsemi.android.meshprovisioner.utils.NetworkTransmitSettings;
import no.nordicsemi.android.meshprovisioner.utils.RelaySettings;

@SuppressWarnings("WeakerAccess")
class DefaultNoOperationMessageState extends MeshMessageState {


    private static final String TAG = DefaultNoOperationMessageState.class.getSimpleName();

    DefaultNoOperationMessageState(@NonNull final Context context,
                                   @Nullable final MeshMessage meshMessage,
                                   @NonNull final MeshTransport meshTransport,
                                   @NonNull final InternalMeshMsgHandlerCallbacks callbacks) {
        super(context, meshMessage, meshTransport, callbacks);
    }

    @Override
    public MessageState getState() {
        return null;
    }

    void parseMeshPdu(final byte[] pdu) {
        final Message message = mMeshTransport.parsePdu(mSrc, pdu);
        if (message != null) {
            if (message instanceof AccessMessage) {
                parseAccessMessage((AccessMessage) message);
            } else {
                parseControlMessage((ControlMessage) message);
            }
        } else {
            Log.v(TAG, "Message reassembly may not be completed yet!");
        }
    }

    /**
     * Parses Access message received
     *
     * @param message access message received by the acccess layer
     */
    private void parseAccessMessage(final AccessMessage message){
        final byte[] accessPayload = message.getAccessPdu();
        final int opCodeLength = ((accessPayload[0] & 0xF0) >> 6);
        switch (opCodeLength) {
            case 0:
                if (message.getOpCode() == ConfigMessageOpCodes.CONFIG_COMPOSITION_DATA_STATUS) {
                    final ConfigCompositionDataStatus status = new ConfigCompositionDataStatus(mNode, message);
                    mNode.setCompositionData(status);
                    mInternalTransportCallbacks.updateMeshNetwork(status);
                    mMeshStatusCallbacks.onMeshMessageReceived(status);
                }
                break;
            case 1:
                if (message.getOpCode() == ApplicationMessageOpCodes.SCENE_STATUS) {
                    final SceneStatus sceneStatus = new SceneStatus(mNode, message);
                    mInternalTransportCallbacks.updateMeshNetwork(sceneStatus);
                    mMeshStatusCallbacks.onMeshMessageReceived(sceneStatus);
                }
                break;
            case 2:
                if (message.getOpCode() == ConfigMessageOpCodes.CONFIG_APPKEY_STATUS) {
                    final ConfigAppKeyStatus status = new ConfigAppKeyStatus(mNode, message);
                    if (status.isSuccessful()) {
                        if (mMeshMessage instanceof ConfigAppKeyAdd) {
                            final ConfigAppKeyAdd configAppKeyAdd = (ConfigAppKeyAdd) mMeshMessage;
                            mNode.setAddedAppKey(status.getAppKeyIndex(), configAppKeyAdd.getAppKey());//MeshParserUtils.bytesToHex(configAppKeyAdd.getAppKey(), false));
                        }
                    }
                    mInternalTransportCallbacks.updateMeshNetwork(status);
                    mMeshStatusCallbacks.onMeshMessageReceived(status);
                } else if (message.getOpCode() == ConfigMessageOpCodes.CONFIG_MODEL_APP_STATUS) {
                    final ConfigModelAppStatus status = new ConfigModelAppStatus(mNode, message);
                    if (status.isSuccessful()) {
                        if(mMeshMessage instanceof ConfigModelAppBind) {
                            mNode.setAppKeyBindStatus(status);
                        } else {
                            mNode.setAppKeyUnbindStatus(status);
                        }
                    }
                    mInternalTransportCallbacks.updateMeshNetwork(status);
                    mMeshStatusCallbacks.onMeshMessageReceived(status);

                }  else if (message.getOpCode() == ConfigMessageOpCodes.CONFIG_MODEL_PUBLICATION_STATUS) {
                    final ConfigModelPublicationStatus status = new ConfigModelPublicationStatus(mNode, message);
                    if (status.isSuccessful()) {
                        final Element element = mNode.getElements().get(status.getElementAddress());
                        final MeshModel model = element.getMeshModels().get(status.getModelIdentifier());
                        model.setPublicationStatus(status);
                    }
                    mInternalTransportCallbacks.updateMeshNetwork(status);
                    mMeshStatusCallbacks.onMeshMessageReceived(status);

                } else if (message.getOpCode() == ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_STATUS) {
                    final ConfigModelSubscriptionStatus status = new ConfigModelSubscriptionStatus(mNode, message);

                    if (status.isSuccessful()) {
                        final Element element = mNode.getElements().get(status.getElementAddress());
                        final MeshModel model = element.getMeshModels().get(status.getModelIdentifier());

                        if (mMeshMessage instanceof ConfigModelSubscriptionAdd) {
                            model.addSubscriptionAddress(status.getSubscriptionAddress());
                        } else if (mMeshMessage instanceof ConfigModelSubscriptionDelete) {
                            model.removeSubscriptionAddress(status.getSubscriptionAddress());
                        }
                    }
                    mInternalTransportCallbacks.updateMeshNetwork(status);
                    mMeshStatusCallbacks.onMeshMessageReceived(status);
                } else if (message.getOpCode() == ConfigMessageOpCodes.CONFIG_NODE_RESET_STATUS) {
                    final ConfigNodeResetStatus status = new ConfigNodeResetStatus(mNode, message);
                    mInternalTransportCallbacks.onMeshNodeReset(mNode);
                    mMeshStatusCallbacks.onMeshMessageReceived(status);
                } else if (message.getOpCode() == ConfigMessageOpCodes.CONFIG_NETWORK_TRANSMIT_STATUS) {
                    final ConfigNetworkTransmitStatus status = new ConfigNetworkTransmitStatus(mNode, message);
                    final NetworkTransmitSettings networkTransmitSettings =
                            new NetworkTransmitSettings(status.getNetworkTransmitCount(), status.getNetworkTransmitIntervalSteps());
                    mNode.setNetworkTransmitSettings(networkTransmitSettings);
                    mInternalTransportCallbacks.updateMeshNetwork(status);
                    mMeshStatusCallbacks.onMeshMessageReceived(status);
                } else if (message.getOpCode() == ConfigMessageOpCodes.CONFIG_RELAY_STATUS) {
                    final ConfigRelayStatus status = new ConfigRelayStatus(mNode, message);
                    final RelaySettings relaySettings =
                            new RelaySettings(status.getRelayRetransmitCount(), status.getRelayRetransmitIntervalSteps());
                    mNode.setRelaySettings(relaySettings);
                    mInternalTransportCallbacks.updateMeshNetwork(status);
                    mMeshStatusCallbacks.onMeshMessageReceived(status);
                } else if (message.getOpCode() == ConfigMessageOpCodes.CONFIG_GATT_PROXY_STATUS) {
                    final ConfigProxyStatus status = new ConfigProxyStatus(mNode, message);
                    mInternalTransportCallbacks.updateMeshNetwork(status);
                    mMeshStatusCallbacks.onMeshMessageReceived(status);
                } else if (message.getOpCode() == ApplicationMessageOpCodes.GENERIC_ON_OFF_STATUS) {
                    final GenericOnOffStatus status = new GenericOnOffStatus(mNode, message);
                    mInternalTransportCallbacks.updateMeshNetwork(status);
                    mMeshStatusCallbacks.onMeshMessageReceived(status);
                } else if (message.getOpCode() == ApplicationMessageOpCodes.GENERIC_LEVEL_STATUS) {
                    final GenericLevelStatus genericLevelStatus = new GenericLevelStatus(mNode, message);
                    mInternalTransportCallbacks.updateMeshNetwork(genericLevelStatus);
                    mMeshStatusCallbacks.onMeshMessageReceived(genericLevelStatus);
                } else if (message.getOpCode() == ApplicationMessageOpCodes.LIGHT_LIGHTNESS_STATUS) {
                    final LightLightnessStatus lightLightnessStatus = new LightLightnessStatus(mNode, message);
                    mInternalTransportCallbacks.updateMeshNetwork(lightLightnessStatus);
                    mMeshStatusCallbacks.onMeshMessageReceived(lightLightnessStatus);
                } else if (message.getOpCode() == ApplicationMessageOpCodes.LIGHT_CTL_STATUS) {
                    final LightCtlStatus lightCtlStatus = new LightCtlStatus(mNode, message);
                    mInternalTransportCallbacks.updateMeshNetwork(lightCtlStatus);
                    mMeshStatusCallbacks.onMeshMessageReceived(lightCtlStatus);
                } else if (message.getOpCode() == ApplicationMessageOpCodes.LIGHT_HSL_STATUS) {
                    final LightHslStatus lightHslStatus = new LightHslStatus(mNode, message);
                    mInternalTransportCallbacks.updateMeshNetwork(lightHslStatus);
                    mMeshStatusCallbacks.onMeshMessageReceived(lightHslStatus);
                } else if (message.getOpCode() == ApplicationMessageOpCodes.SCENE_REGISTER_STATUS) {
                    final SceneRegisterStatus registerStatus = new SceneRegisterStatus(mNode, message);
                    mInternalTransportCallbacks.updateMeshNetwork(registerStatus);
                    mMeshStatusCallbacks.onMeshMessageReceived(registerStatus);
                } else {
                    Log.v(TAG, "Unknown Access PDU Received: " + MeshParserUtils.bytesToHex(accessPayload, false));
                }
                break;
            case 3:
                if(mMeshMessage instanceof VendorModelMessageAcked) {
                    final VendorModelMessageAcked vendorModelMessageAcked = (VendorModelMessageAcked) mMeshMessage;
                    final VendorModelMessageStatus status = new VendorModelMessageStatus(mNode, message, vendorModelMessageAcked.getModelIdentifier());
                    mMeshStatusCallbacks.onMeshMessageReceived(status);
                    Log.v(TAG, "Vendor model Access PDU Received: " + MeshParserUtils.bytesToHex(accessPayload, false));
                } else if(mMeshMessage instanceof  VendorModelMessageUnacked) {
                    final VendorModelMessageUnacked vendorModelMessageUnacked = (VendorModelMessageUnacked) mMeshMessage;
                    final VendorModelMessageStatus status = new VendorModelMessageStatus(mNode, message, vendorModelMessageUnacked.getModelIdentifier());
                    mMeshStatusCallbacks.onMeshMessageReceived(status);
                }
                break;
            default:
                Log.v(TAG, "Unknown Access PDU Received: " + MeshParserUtils.bytesToHex(accessPayload, false));
                mMeshStatusCallbacks.onUnknownPduReceived(mNode, AddressUtils.getUnicastAddressInt(message.getSrc()), message.getAccessPdu());
                break;
        }
    }

    /**
     * Parses control message received
     *
     * @param controlMessage control message received by the transport layer
     */
    private void parseControlMessage(final ControlMessage controlMessage){
        //Get the segment count count of the access message
        final int segmentCount = message.getNetworkPdu().size();
        final TransportControlMessage transportControlMessage = controlMessage.getTransportControlMessage();
        switch (transportControlMessage.getState()) {
            case LOWER_TRANSPORT_BLOCK_ACKNOWLEDGEMENT:
                Log.v(TAG, "Acknowledgement payload: " + MeshParserUtils.bytesToHex(controlMessage.getTransportControlPdu(), false));
                final ArrayList<Integer> retransmitPduIndexes = BlockAcknowledgementMessage.getSegmentsToBeRetransmitted(controlMessage.getTransportControlPdu(), segmentCount);
                mMeshStatusCallbacks.onBlockAcknowledgementReceived(mNode);
                executeResend(retransmitPduIndexes);
                break;
            default:
                Log.v(TAG, "Unexpected control message received, ignoring message");
                mMeshStatusCallbacks.onUnknownPduReceived(mNode, AddressUtils.getUnicastAddressInt(controlMessage.getSrc()), controlMessage.getTransportControlPdu());
                break;
        }
    }
}
